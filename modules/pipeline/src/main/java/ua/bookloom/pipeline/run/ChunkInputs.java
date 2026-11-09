package ua.bookloom.pipeline.run;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.CharacterSheet;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.glossary.GlossaryNames;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.lexicon.Lexicon;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpan;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.typography.TypographyGate;

/**
 * One chunk's glossary as it stood when the chunk started, and what the run reads of it besides the context builder:
 * the spans each segment hides behind tokens, the gate that puts them back, and the names, terms and sheets the checks
 * and the reviewer hold the chunk to. Reading the glossary once per chunk is what makes an edit saved during a pause
 * inside the chunk wait for the next chunk.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ChunkInputs {

    private final Chunk chunk;
    private final RunSettings settings;
    private final List<GlossaryEntry> glossary;
    private final Map<String, ProtectedMask> masks;
    private final GateFunction gate;
    private final Lexicon lexicon;

    private ChunkInputs(
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
    }

    /**
     * Reads the glossary for one chunk and hides each of its segments' protected spans.
     *
     * @param glossary the non-null glossary store
     * @param lexicon the non-null lexicon store, read live because a batch of this chunk may add to it
     * @param settings the non-null run settings, whose languages and foreign-passage policy decide what is hidden
     * @param chunk the non-null chunk about to be drafted
     * @param documentGate the non-null gate that checks the document's own markup once the spans are back
     * @return the chunk's inputs, or the glossary's error
     */
    static Result<ChunkInputs> read(
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
                    ProtectedSpans.gate(masks, documentGate),
                    settings.frame().targetLanguage(),
                    settings.frame().typography());
            return new ChunkInputs(chunk, settings, entries, masks, gate, new Lexicon(lexicon));
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
        final List<String> terms = GlossaryNames.terms(occurringIn(chunk.segments(), glossary));
        log.debug("Read the glossary for a chunk entries={} inChunk={}", glossary.size(), terms.size());
        return terms;
    }

    /** The unlocked glossary renderings of the chunk's terms as {@code source → target} lines: what the name checks hold the text to. */
    List<String> termPairs() {
        final List<String> pairs = GlossaryNames.pairs(occurringIn(chunk.segments(), glossary).stream()
                .filter(entry -> !entry.locked())
                .toList());
        log.debug("Glossary pairs for a chunk pairs={}", pairs.size());
        return pairs;
    }

    /** The glossary entries whose names occur in the chunk, which the one repair's name vetoes read. */
    List<GlossaryEntry> names() {
        return occurringIn(chunk.segments(), glossary);
    }

    /** The renderings of the chunk's pinned terms, whose declined forms the name-spelling check accepts. */
    List<String> pinnedTargets() {
        return GlossaryNames.pinnedTargets(occurringIn(chunk.segments(), glossary));
    }

    /**
     * The character gender sheet of the chunk, cut to the sheet's own share of the dynamic allowance.
     */
    List<String> characterLines() {
        final List<String> lines = TokenEstimator.leadingWithin(
                CharacterSheet.select(
                        chunk.segments(), glossary, settings.frame().analysisLanguage()),
                ContextBudget.characterAllowance(ChunkBudget.dynamicAllowance(settings.frame(), settings.window())));
        log.debug("Character sheet lines={}", lines.size());
        return lines;
    }

    /** The run's lexicon, whose entries the book map reads as they stand. */
    Lexicon lexicon() {
        return lexicon;
    }

    private static List<String> textsOf(final List<Segment> segments) {
        return Tokens.visibleTexts(segments);
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
                .filter(entry -> texts.stream().anyMatch(text -> GlossaryNames.isNamedIn(entry, text)))
                .toList();
    }
}
