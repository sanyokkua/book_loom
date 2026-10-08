package ua.bookloom.pipeline.run;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.context.ContextInputs;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.context.ContextPackageAssembler;
import ua.bookloom.pipeline.context.InjectedCharacters;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.lexicon.Lexicon;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpan;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.typography.TypographyGate;

/**
 * One chunk's glossary as it stood when the chunk started, and what each of its segments is shown from it: the spans
 * hidden behind tokens, the gate that puts them back, and the context package. Reading the glossary once per chunk is
 * what makes an edit saved during a pause inside the chunk wait for the next chunk.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ChunkContext {

    private final Chunk chunk;
    private final RunSettings settings;
    private final List<GlossaryEntry> glossary;
    private final Map<String, ProtectedMask> masks;
    private final GateFunction gate;
    private final Lexicon lexicon;
    private final LexiconFilter lexiconFilter;

    private ChunkContext(
            final Chunk chunk,
            final RunSettings settings,
            final List<GlossaryEntry> glossary,
            final Map<String, ProtectedMask> masks,
            final GateFunction gate,
            final Lexicon lexicon) {
        this.chunk = chunk;
        this.settings = settings;
        this.glossary = List.copyOf(glossary);
        this.masks = Map.copyOf(masks);
        this.gate = gate;
        this.lexicon = lexicon;
        this.lexiconFilter = LexiconFilter.of(
                settings.frame().sourceLanguage(), settings.frame().targetLanguage());
    }

    /**
     * Reads the glossary for one chunk and hides each of its segments' protected spans.
     *
     * @param glossary the non-null glossary store
     * @param lexicon the non-null lexicon store, read live because a batch of this chunk may add to it
     * @param settings the non-null run settings, whose languages and foreign-passage policy decide what is hidden
     * @param chunk the non-null chunk about to be drafted
     * @param documentGate the non-null gate that checks the document's own markup once the spans are back
     * @return the chunk's context, or the glossary's error
     */
    static Result<ChunkContext> read(
            final GlossaryRepository glossary,
            final LexiconRepository lexicon,
            final RunSettings settings,
            final Chunk chunk,
            final GateFunction documentGate) {
        Objects.requireNonNull(documentGate, "documentGate");
        return glossary.all(settings.projectId()).map(entries -> {
            final Map<String, ProtectedMask> masks = new LinkedHashMap<>();
            chunk.segments().forEach(segment -> masks.put(segment.id(), maskOf(segment, settings, entries)));
            final GateFunction gate = TypographyGate.around(
                    ProtectedSpans.gate(masks, documentGate), settings.frame().targetLanguage());
            return new ChunkContext(chunk, settings, entries, masks, gate, new Lexicon(lexicon));
        });
    }

    /** The segment's text with its protected spans hidden, as the model is shown it. */
    ProtectedMask mask(final Segment segment) {
        return Objects.requireNonNull(masks.get(segment.id()), () -> "no mask for " + segment.id());
    }

    /** Puts a candidate's protected spans back, then checks the document's own markup. */
    GateFunction gate() {
        return gate;
    }

    /** The glossary as it stood when the chunk started, which a decided segment's unknown-gender check reads. */
    List<GlossaryEntry> glossary() {
        return glossary;
    }

    /** The terms of the glossary entries that occur in the chunk, read by the checks' name removal. */
    List<String> terms() {
        final List<String> terms = occurringIn(chunk.segments(), glossary).stream()
                .map(GlossaryEntry::term)
                .toList();
        log.debug("Read the glossary for a chunk entries={} inChunk={}", glossary.size(), terms.size());
        return terms;
    }

    /** The unlocked glossary renderings of the chunk's terms as {@code source → target} lines: what the reviewer holds the text to. */
    List<String> termPairs() {
        final List<String> pairs = occurringIn(chunk.segments(), glossary).stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank() && !entry.locked())
                .map(entry -> entry.term() + " → " + entry.target())
                .toList();
        log.debug("Reviewer glossary pairs for a chunk pairs={}", pairs.size());
        return pairs;
    }

    /**
     * The lexicon's established renderings of the recurring terms the chunk names and the glossary does not hold, as
     * {@code source → target} lines: the usual renderings, which the reviewer may see differ.
     */
    List<String> usualRenderings() {
        final List<String> texts = textsOf(chunk.segments());
        final List<String> pairs = lexicon.entries(settings.projectId()).stream()
                .filter(entry -> !heldByGlossary(entry))
                .filter(entry -> texts.stream().anyMatch(text -> TermMatch.isNamedIn(entry.term(), text)))
                .flatMap(entry -> entry
                        .established()
                        .filter(rendering -> lexiconFilter.admits(entry.term(), rendering))
                        .map(rendering -> entry.term() + " → " + rendering)
                        .stream())
                .toList();
        log.debug("Reviewer usual renderings for a chunk pairs={}", pairs.size());
        return pairs;
    }

    /**
     * The recurring key terms the given segments name and the glossary does not decide: the closed list a batch asks
     * the model to report renderings for.
     */
    List<String> keyTermsIn(final List<Segment> segments) {
        final List<LexiconEntry> open = lexicon.entries(settings.projectId()).stream()
                .filter(entry -> !heldByGlossary(entry))
                .toList();
        return Lexicon.termsIn(open, textsOf(segments));
    }

    /**
     * The character gender sheet of the chunk, cut to the sheet's own share of the dynamic allowance, for the reviewer.
     */
    List<String> characterLines() {
        final List<String> lines = InjectedCharacters.within(
                InjectedCharacters.select(
                        chunk.segments(), glossary, settings.frame().sourceLanguage()),
                ContextBudget.characterAllowance(ChunkBudget.dynamicAllowance(settings.frame(), settings.window())));
        log.debug("Reviewer character sheet lines={}", lines.size());
        return lines;
    }

    /** The run's lexicon, to which a verified pair is recorded. */
    Lexicon lexicon() {
        return lexicon;
    }

    private boolean heldByGlossary(final LexiconEntry entry) {
        final String key = LexiconEntry.keyOf(entry.term());
        return glossary.stream()
                .anyMatch(held -> LexiconEntry.keyOf(held.term()).equals(key));
    }

    private static List<String> textsOf(final List<Segment> segments) {
        return Tokens.visibleTexts(segments);
    }

    /**
     * Assembles what one draft is shown besides its source, and the snapshot of it its record stores — a memory
     * reuse's too, though no draft is made for it.
     *
     * @param segment the segment about to be drafted
     * @param earlierMaskedTargets the masked targets of the unit's earlier segments, in document order
     * @param memory what the translation memory offers the segment
     * @param summary the latest rolling summary's text, or {@code null} while there is none
     * @return the draft's context and its snapshot
     */
    ContextPackage contextFor(
            final Segment segment,
            final List<String> earlierMaskedTargets,
            final TmLookup memory,
            @Nullable final String summary) {
        final ContextInputs inputs = new ContextInputs(
                settings.frame().styleSheet(),
                summary,
                settings.dial().precedingTargets(),
                glossary,
                earlierMaskedTargets,
                ChunkBudget.dynamicAllowance(settings.frame(), settings.window()),
                lexicon.entries(settings.projectId()),
                lexiconFilter,
                settings.frame().sourceLanguage());
        return ContextPackageAssembler.assemble(chunk, segment, mask(segment), memory, inputs);
    }

    private static ProtectedMask maskOf(
            final Segment segment, final RunSettings settings, final List<GlossaryEntry> entries) {
        final ProtectedMask mask = ProtectedSpans.mask(segment, settings.frame(), entries);
        log.debug(
                "Protected mask segmentId={} keptRunTokens={} lockedTermTokens={}",
                segment.id(),
                tokensOf(mask, CheckName.KEPT_RUN),
                tokensOf(mask, CheckName.LOCKED_TERM));
        return mask;
    }

    private static List<String> tokensOf(final ProtectedMask mask, final CheckName check) {
        return mask.spans().stream()
                .filter(span -> span.check() == check)
                .map(ProtectedSpan::token)
                .toList();
    }

    private static List<GlossaryEntry> occurringIn(final List<Segment> segments, final List<GlossaryEntry> entries) {
        final List<String> texts = textsOf(segments);
        return entries.stream()
                .filter(entry -> texts.stream().anyMatch(text -> TermMatch.isNamedIn(entry.term(), text)))
                .toList();
    }
}
