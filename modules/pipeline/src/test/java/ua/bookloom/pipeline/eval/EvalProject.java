package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.context.AcceptedTargets;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.PromptRequests;
import ua.bookloom.pipeline.run.PromptRequests.BatchSlot;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;
import ua.bookloom.pipeline.run.RunSettings;

/**
 * The project a prompt eval case stands for, held in the places a run holds its own: the glossary and lexicon in the
 * in-memory stores, the segments of one unit, the brief's style sheet and narrator, the quality dial's counts and the
 * window — and every request is built by the run's own {@link PromptRequests}. An eval therefore sends what the app
 * sends; the only inputs it makes up are the ones the case file states.
 *
 * <p>The earlier pairs of the case are the unit's segments before the chunk, accepted with the case's targets, so a
 * call's window reads them as a run reads what it accepted; the rolling summary no longer reaches a draft (design D10).
 */
public final class EvalProject {

    private static final String PROJECT = "eval";
    private static final String UNIT = "eval";
    private static final PromptTemplates TEMPLATES = EvalVariants.templates();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DocumentPort DOCUMENTS =
            Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    private static final TmLookup NO_MEMORY = new TmLookup(null, List.of(), List.of());

    /** What a project is built from. */
    public record Setup(
            String sourceLanguage,
            String targetLanguage,
            @Nullable String bookLanguage,
            List<EvalTerm> glossary,
            EvalContext context,
            List<String> chunk,
            List<String> after) {

        /** Copies the lists. */
        public Setup {
            glossary = List.copyOf(glossary);
            chunk = List.copyOf(chunk);
            after = List.copyOf(after);
        }

        /** A project of one segment. */
        public static Setup single(
                final String sourceLanguage,
                final String targetLanguage,
                final List<EvalTerm> glossary,
                final EvalContext context,
                final String text) {
            return new Setup(sourceLanguage, targetLanguage, null, glossary, context, List.of(text), List.of());
        }
    }

    private final RunSettings settings;
    private final PromptRequests requests;
    private final BatchDrafter drafter;
    private final List<Segment> chunk;
    private final AcceptedTargets accepted;
    private final TranslatePlanner planner;

    private EvalProject(
            final RunSettings settings,
            final PromptRequests requests,
            final BatchDrafter drafter,
            final List<Segment> chunk,
            final AcceptedTargets accepted,
            final TranslatePlanner planner) {
        this.settings = settings;
        this.requests = requests;
        this.drafter = drafter;
        this.chunk = chunk;
        this.accepted = accepted;
        this.planner = planner;
    }

    /** The window every eval request is sized against; see {@link EvalWindow#window()}. */
    public static int window() {
        return EvalWindow.window();
    }

    /** The window for an asked-for value when the provider reported none. */
    static int window(@Nullable final String asked) {
        return EvalWindow.resolve(asked, null);
    }

    public static EvalProject of(final Setup setup, final ModelCalls calls) {
        return of(setup, calls, window());
    }

    public static EvalProject of(final Setup setup, final ModelCalls calls, final int window) {
        Objects.requireNonNull(setup, "setup");
        return of(setup, calls, window, EvalBrief.of(setup.sourceLanguage(), setup.targetLanguage(), System.getenv()));
    }

    private static RunSettings settingsOf(final BookBrief brief, final CallFrame frame, final int window) {
        return new RunSettings(
                PROJECT, ReviewMode.UNATTENDED, DialParameters.of(brief.dial()), frame, brief.names(), window);
    }

    /**
     * A project whose brief is given instead of read from the environment; the case's narrator still applies. The
     * case's earlier pairs are the unit's segments before the chunk, accepted with their targets, so a call's window
     * reads them as a run reads the segments it accepted.
     */
    static EvalProject of(final Setup setup, final ModelCalls calls, final int window, final BookBrief chosen) {
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(calls, "calls");
        final BookBrief brief =
                withNarrator(chosen, setup).withLanguages(setup.sourceLanguage(), setup.targetLanguage());
        final CallFrame frame = new CallFrame(
                setup.sourceLanguage(),
                setup.targetLanguage(),
                StyleSheet.from(brief),
                brief.foreignPassages(),
                setup.bookLanguage(),
                brief.narrator());
        final RunSettings settings = settingsOf(brief, frame, window);
        final Stores stores = stores(setup);
        final EvalBook book = EvalBook.of(setup, brief, stores.glossary(), stores.lexicon(), window, TEMPLATES);
        final TranslatePlanner planner = book.planner();
        final List<Segment> chunk = book.chunk();
        final BatchDrafter drafter = new BatchDrafter(
                new BatchPromptBuilder(planner),
                new BatchReplyParser(MAPPER),
                calls,
                frame.sourceLanguage(),
                frame.targetLanguage(),
                BatchDrafter.DEFAULT_INITIAL_SIZE);
        final PromptRequests requests = requestsOf(stores, settings, chunk, calls, drafter, planner);
        return new EvalProject(settings, requests, drafter, chunk, book.accepted(), planner);
    }

    /** The glossary and the lexicon in the in-memory stores a run reads them from. */
    private record Stores(GlossaryRepository glossary, LexiconRepository lexicon) {}

    private static Stores stores(final Setup setup) {
        final var injector = Guice.createInjector(new InMemoryPersistenceModule());
        final GlossaryRepository glossary = injector.getInstance(GlossaryRepository.class);
        final LexiconRepository lexicon = injector.getInstance(LexiconRepository.class);
        setup.glossary().forEach(term -> glossary.add(term.entry(PROJECT)));
        setup.context().lexicon().forEach(entry -> lexicon.put(lexiconEntry(entry)));
        return new Stores(glossary, lexicon);
    }

    private static PromptRequests requestsOf(
            final Stores stores,
            final RunSettings settings,
            final List<Segment> chunk,
            final ModelCalls calls,
            final BatchDrafter drafter,
            final TranslatePlanner planner) {
        final GateFunction documentGate = GateFunction.of(DOCUMENTS, BookFormat.TXT);
        final SegmentTranslator translator =
                new SegmentTranslator(documentGate, calls, BookFormat.TXT, planner, MAPPER);
        final Result<PromptRequests> read = PromptRequests.read(
                stores.glossary(),
                stores.lexicon(),
                settings,
                new Chunk(UNIT, chunk, false),
                documentGate,
                new PromptRequests.Steps(translator, drafter, planner));
        return Objects.requireNonNull(read.data(), () -> "chunk context: " + read.error());
    }

    private static BookBrief withNarrator(final BookBrief brief, final Setup setup) {
        return setup.context().narrator() == null
                ? brief
                : brief.withNarrator(setup.context().narrator());
    }

    private static LexiconEntry lexiconEntry(final EvalContext.Rendering rendering) {
        return new LexiconEntry(PROJECT, rendering.term(), List.of(), rendering.rendering(), null, null);
    }

    static List<Segment> segments(final List<String> texts, final int offset) {
        final List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            segments.add(segment(offset + i, texts.get(i)));
        }
        return segments;
    }

    /** A paragraph of the eval's one unit, whose text is already masked as the document model masks inline markup. */
    static Segment segment(final int order, final String masked) {
        return new Segment(
                UNIT + ":" + order,
                UNIT,
                order,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    /**
     * The segment at {@code index} as the document module would parse it: a map entry for each token and the tokens
     * paired as they open and close, so the run's own gate can restore and judge a reply. The request-building segment
     * has neither, which is what keeps the repair cases' rejected targets in need of a model.
     */
    public Segment judged(final int index) {
        final Segment bare = chunk.get(index);
        return new Segment(
                bare.id(),
                bare.unit(),
                bare.order(),
                bare.kind(),
                bare.sourceInner(),
                bare.masked(),
                placeholders(bare.masked()),
                bare.sourceHash(),
                bare.prevKey(),
                bare.nextKey(),
                bare.anchor(),
                bare.targetInner(),
                bare.status(),
                bare.confidence(),
                null,
                pairs(bare.masked()),
                List.of());
    }

    /** The chunk gate a run restores replies through, so a reply is judged by the same placeholder rules. */
    public GateFunction gate() {
        return requests.gate();
    }

    /** The run's draft step over this project, whose {@code adopt} reads a reply with no repair. */
    public SegmentTranslator translator() {
        return requests.translator();
    }

    // The document gate restores each token from the segment's map, so every token the text holds needs an entry; the
    // eval's text has no markup behind a token, so the original is empty.
    private static java.util.Map<String, String> placeholders(final String masked) {
        final java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        Tokens.inOrder(masked).forEach(token -> map.put(token.substring(1, token.length() - 1), ""));
        return map;
    }

    // The document module pairs the tokens of an inline element; the eval's texts hold either one lone token or tokens
    // that open and close in turn.
    private static List<PlaceholderPair> pairs(final String masked) {
        final List<String> tokens = Tokens.inOrder(masked);
        if (tokens.size() < 2 || tokens.size() % 2 != 0) {
            return List.of();
        }
        return IntStream.range(0, tokens.size() / 2)
                .mapToObj(i -> new PlaceholderPair(tokens.get(2 * i), tokens.get(2 * i + 1), null))
                .toList();
    }

    public RunSettings settings() {
        return settings;
    }

    public CallFrame frame() {
        return settings.frame();
    }

    public PromptRequests requests() {
        return requests;
    }

    public Segment segment(final int index) {
        return chunk.get(index);
    }

    /** The text of the segment as a model is shown it, its protected spans hidden. */
    public ProtectedMask mask(final int index) {
        return requests.mask(chunk.get(index));
    }

    public LoopSettings loop() {
        return requests.loopSettings();
    }

    /** The planner of this project's translate-family calls, bound to the frame a run on it starts with. */
    public TranslatePlanner planner() {
        return planner;
    }

    /** The earlier segments' kept targets, as a run reads the segments it accepted before the chunk. */
    public AcceptedTargets accepted() {
        return accepted;
    }

    /** The window, map and memory a single draft of the segment at {@code index} is shown. */
    public TranslateContext translateContext(final int index) {
        final Segment segment = chunk.get(index);
        final Result<TranslateContext> read = requests.translateContext(
                List.of(new BatchSlot(segment, requests.mask(segment), NO_MEMORY)), accepted, List.of());
        return Objects.requireNonNull(read.data(), () -> "window: " + read.error());
    }

    /** The request a run sends for the segment at {@code index}'s first draft. */
    public ChatRequest draftRequest(final int index) {
        return requests.draftRequest(chunk.get(index), translateContext(index), mask(index));
    }

    /** The batch of the whole chunk as the run would make it, or empty when the window takes fewer than two. */
    Optional<PreparedBatch> batch() {
        return batch(0, chunk.size());
    }

    /**
     * The batch a run makes from {@code count} segments of the chunk that start at {@code from}, after the earlier
     * pairs the case states, or empty when the window takes fewer than two.
     */
    public Optional<PreparedBatch> batch(final int from, final int count) {
        final List<BatchSlot> slots = chunk.subList(from, from + count).stream()
                .map(segment -> new BatchSlot(segment, requests.mask(segment), NO_MEMORY))
                .toList();
        final Result<Optional<PreparedBatch>> prepared = requests.batch(slots, accepted);
        return Objects.requireNonNull(prepared.data(), () -> "batch: " + prepared.error());
    }

    /** The request of a prepared batch's one call. */
    public ChatRequest batchRequest(final PreparedBatch prepared) {
        return requests.batchRequest(prepared);
    }

    /** The drafter that reads a batch's reply and sends its call. */
    public BatchDrafter drafter() {
        return drafter;
    }
}
