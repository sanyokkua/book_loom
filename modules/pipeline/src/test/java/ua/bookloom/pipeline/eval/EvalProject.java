package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.DocumentPort;
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
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.EarlierPair;
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
 * <p>The earlier pairs of the unit and the summary are read the way a run reads them: the dial's count of the latest
 * pairs, and a batch's own count with the production cap.
 */
public final class EvalProject {

    private static final String PROJECT = "eval";
    private static final String UNIT = "eval";
    private static final PromptTemplates TEMPLATES = new PromptTemplates();
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
    private final EvalContext context;
    private final List<Segment> chunk;
    private final List<Segment> unit;

    private EvalProject(
            final RunSettings settings,
            final PromptRequests requests,
            final BatchDrafter drafter,
            final EvalContext context,
            final List<Segment> chunk,
            final List<Segment> unit) {
        this.settings = settings;
        this.requests = requests;
        this.drafter = drafter;
        this.context = context;
        this.chunk = chunk;
        this.unit = unit;
    }

    /** The window every eval request is sized against: {@code BOOKLOOM_EVAL_WINDOW}, else what an app run uses. */
    public static int window() {
        final String asked = System.getenv("BOOKLOOM_EVAL_WINDOW");
        return asked == null || asked.isBlank() ? ContextBudget.windowFor(null, null) : Integer.parseInt(asked.strip());
    }

    public static EvalProject of(final Setup setup, final ModelCalls calls) {
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(calls, "calls");
        final BookBrief brief = briefOf(setup);
        final CallFrame frame = new CallFrame(
                setup.sourceLanguage(),
                setup.targetLanguage(),
                StyleSheet.from(brief),
                brief.foreignPassages(),
                setup.bookLanguage(),
                brief.narrator(),
                WordValidator.none());
        final RunSettings settings = new RunSettings(
                PROJECT, ReviewMode.UNATTENDED, DialParameters.of(brief.dial()), frame, brief.names(), window());
        final List<Segment> chunk = segments(setup.chunk(), 0);
        final List<Segment> unit = new ArrayList<>(chunk);
        unit.addAll(segments(setup.after(), chunk.size()));
        final BatchDrafter drafter = new BatchDrafter(
                new BatchPromptBuilder(TEMPLATES, frame),
                new BatchReplyParser(MAPPER),
                calls,
                frame.sourceLanguage(),
                frame.targetLanguage(),
                BatchDrafter.DEFAULT_INITIAL_SIZE);
        final PromptRequests requests = requestsOf(setup, settings, chunk, calls, drafter);
        return new EvalProject(settings, requests, drafter, setup.context(), chunk, unit);
    }

    // The glossary and the lexicon sit in the in-memory stores a run reads them from.
    private static PromptRequests requestsOf(
            final Setup setup,
            final RunSettings settings,
            final List<Segment> chunk,
            final ModelCalls calls,
            final BatchDrafter drafter) {
        final var injector = Guice.createInjector(new PersistenceModule());
        final GlossaryRepository glossary = injector.getInstance(GlossaryRepository.class);
        final LexiconRepository lexicon = injector.getInstance(LexiconRepository.class);
        setup.glossary().forEach(term -> glossary.add(term.entry(PROJECT)));
        setup.context().lexicon().forEach(entry -> lexicon.put(lexiconEntry(entry)));
        final GateFunction documentGate = GateFunction.of(DOCUMENTS, BookFormat.TXT);
        final SegmentTranslator translator = new SegmentTranslator(
                documentGate,
                calls,
                BookFormat.TXT,
                new DraftPromptBuilder(TEMPLATES, settings.frame()),
                new DraftReplyParser(MAPPER));
        final Result<PromptRequests> read = PromptRequests.read(
                glossary, lexicon, settings, new Chunk(UNIT, chunk, false), documentGate, translator, drafter);
        return Objects.requireNonNull(read.data(), () -> "chunk context: " + read.error());
    }

    private static BookBrief briefOf(final Setup setup) {
        final BookBrief brief = BookBrief.defaults(setup.sourceLanguage())
                .withLanguages(setup.sourceLanguage(), setup.targetLanguage());
        return setup.context().narrator() == null
                ? brief
                : brief.withNarrator(setup.context().narrator());
    }

    private static LexiconEntry lexiconEntry(final EvalContext.Rendering rendering) {
        return new LexiconEntry(PROJECT, rendering.term(), List.of(), rendering.rendering(), null, null);
    }

    private static List<Segment> segments(final List<String> texts, final int offset) {
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
                java.util.Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
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

    /** The last masked targets a single draft shows, as many as the dial says. */
    public List<String> earlierTargets() {
        final List<EvalContext.Pair> all = context.preceding();
        return all.subList(Math.max(0, all.size() - settings.dial().precedingTargets()), all.size()).stream()
                .map(EvalContext.Pair::target)
                .toList();
    }

    /** What a single draft of the segment at {@code index} is shown. */
    public ContextPackage draftContext(final int index) {
        return requests.contextFor(chunk.get(index), earlierTargets(), NO_MEMORY, context.summary());
    }

    /** The request a run sends for the segment at {@code index}'s first draft. */
    public ChatRequest draftRequest(final int index) {
        return requests.draftRequest(chunk.get(index), draftContext(index).draftContext(), mask(index));
    }

    /** The batch of the whole chunk as the run would make it, or empty when the window takes fewer than two. */
    Optional<PreparedBatch> batch() {
        return batch(0, chunk.size());
    }

    /**
     * The batch a run makes from {@code count} segments of the chunk that start at {@code from}, with the earlier pairs
     * the case states, or empty when the window takes fewer than two.
     */
    public Optional<PreparedBatch> batch(final int from, final int count) {
        final List<BatchSlot> slots = chunk.subList(from, from + count).stream()
                .map(segment -> new BatchSlot(segment, requests.mask(segment), NO_MEMORY))
                .toList();
        final List<EvalContext.Pair> all = context.preceding();
        final int first = Math.max(0, all.size() - requests.batchPairCount());
        final List<EarlierPair> earlier = new ArrayList<>();
        for (int i = first; i < all.size(); i++) {
            earlier.add(
                    new EarlierPair(segment(i, all.get(i).source()), all.get(i).target()));
        }
        return requests.batch(slots, unit, earlier, context.summary());
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
