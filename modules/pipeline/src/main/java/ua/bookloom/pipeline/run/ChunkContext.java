package ua.bookloom.pipeline.run;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.WholeWord;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.context.ContextInputs;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.context.ContextPackageAssembler;
import ua.bookloom.pipeline.heal.GateFunction;
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

    private ChunkContext(
            final Chunk chunk,
            final RunSettings settings,
            final List<GlossaryEntry> glossary,
            final Map<String, ProtectedMask> masks,
            final GateFunction gate) {
        this.chunk = chunk;
        this.settings = settings;
        this.glossary = List.copyOf(glossary);
        this.masks = Map.copyOf(masks);
        this.gate = gate;
    }

    /**
     * Reads the glossary for one chunk and hides each of its segments' protected spans.
     *
     * @param glossary the non-null glossary store
     * @param settings the non-null run settings, whose languages and foreign-passage policy decide what is hidden
     * @param chunk the non-null chunk about to be drafted
     * @param documentGate the non-null gate that checks the document's own markup once the spans are back
     * @return the chunk's context, or the glossary's error
     */
    static Result<ChunkContext> read(
            final GlossaryRepository glossary,
            final RunSettings settings,
            final Chunk chunk,
            final GateFunction documentGate) {
        Objects.requireNonNull(documentGate, "documentGate");
        return glossary.all(settings.projectId()).map(entries -> {
            final Map<String, ProtectedMask> masks = new LinkedHashMap<>();
            chunk.segments().forEach(segment -> masks.put(segment.id(), maskOf(segment, settings, entries)));
            final GateFunction gate = TypographyGate.around(
                    ProtectedSpans.gate(masks, documentGate), settings.frame().targetLanguage());
            return new ChunkContext(chunk, settings, entries, masks, gate);
        });
    }

    /**
     * Estimates what the glossary lines of a unit take in the prompt, as the entries stand when the unit is packed.
     * The lines mix the source term and its rendering, so they are estimated at the unknown script's rate.
     *
     * @param segments the non-null segments of the unit
     * @param entries the non-null glossary entries
     * @return the estimated tokens of every entry whose term occurs in the unit; 0 when none does
     */
    static int termsEstimate(final List<Segment> segments, final List<GlossaryEntry> entries) {
        final String lines = occurringIn(segments, entries).stream()
                .map(entry -> entry.term() + " → " + Objects.requireNonNullElse(entry.target(), "") + " ("
                        + entry.type().name().toLowerCase(Locale.ROOT) + ", "
                        + entry.gender().name().toLowerCase(Locale.ROOT) + ")")
                .collect(Collectors.joining("\n"));
        return lines.isEmpty() ? 0 : TokenEstimator.estimate(lines, null);
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
                ChunkBudget.dynamicAllowance(settings.frame(), ContextBudget.DEFAULT_WINDOW));
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
        final List<String> texts = segments.stream()
                .map(segment -> Tokens.replace(segment.masked(), " "))
                .toList();
        return entries.stream()
                .filter(entry -> !entry.term().isBlank())
                .filter(entry -> texts.stream()
                        .anyMatch(text ->
                                WholeWord.pattern(entry.term()).matcher(text).find()))
                .toList();
    }
}
