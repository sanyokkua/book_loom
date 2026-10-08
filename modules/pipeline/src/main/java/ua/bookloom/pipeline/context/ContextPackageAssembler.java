package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.SnapshotTmHit.TmHitKind;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * Builds what one draft is shown besides its source, and the snapshot that lets a retry rebuild it exactly. Preceding
 * targets are shown as plain text: a model shown {@code *перший*} writes {@code *} around its own text.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ContextPackageAssembler {

    /**
     * Assembles one segment's context.
     *
     * @param chunk the chunk holding the segment; the context is scoped to the segment's own text, not the chunk's
     * @param segment the segment about to be drafted
     * @param mask the segment's protected spans, which decide how a locked term is described
     * @param memory what the translation memory offers for the segment
     * @param inputs the run's summary, style sheet, glossary and earlier targets
     * @return the prompt's optional blocks and the snapshot of them; never null
     */
    public static ContextPackage assemble(
            final Chunk chunk,
            final Segment segment,
            final ProtectedMask mask,
            final TmLookup memory,
            final ContextInputs inputs) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(inputs, "inputs");
        final DynamicFit fit = DynamicFit.of(
                InjectedTerms.select(List.of(segment), mask, inputs.glossary()),
                InjectedCharacters.select(List.of(segment), inputs.glossary()),
                InjectedLexicon.select(List.of(segment), inputs.lexicon(), inputs.glossary(), inputs.lexiconFilter()),
                memoryHits(memory),
                precedingTexts(inputs),
                inputs.summary(),
                inputs.dynamicTokens());
        final ContextPackage assembled = packageOf(fit, inputs.styleSheet().text());
        logAssembly(
                segment,
                assembled.snapshot(),
                memoryLines(assembled.snapshot().tmHits()),
                assembled.draftContext(),
                scope(assembled.snapshot(), inputs));
        return assembled;
    }

    private static ContextPackage packageOf(final DynamicFit fit, final String styleSheet) {
        final List<InjectedTerm> terms = fit.terms();
        final DraftContext context = new DraftContext(
                fit.preceding(),
                fit.summary(),
                lines(terms, false),
                memoryLines(fit.hits()),
                lines(terms, true),
                lexiconLines(fit.lexicon()),
                fit.characters());
        final ContextSnapshot snapshot = new ContextSnapshot(
                fit.preceding(),
                terms.stream().map(InjectedTerm::term).toList(),
                fit.hits(),
                fit.summary(),
                styleSheet,
                fit.lexicon(),
                fit.characters());
        return new ContextPackage(context, snapshot);
    }

    /**
     * Rebuilds a draft's context from the texts its snapshot recorded, so a retry is shown what the first draft was
     * shown even after the glossary, the memory or the summary changed. Nothing is looked up.
     *
     * @param snapshot the non-null snapshot the first draft's record stores
     * @param mask the segment's non-null mask, built from the snapshot's terms, which decides how a locked term is
     *     described
     * @param segment the non-null segment being drafted again; the snapshot's terms, renderings and characters the
     *     segment's own text does not name are dropped, so a snapshot taken when contexts were wider shrinks too
     * @return the same preceding targets, memory lines and summary the snapshot was assembled with, and the term
     *     lines, renderings and characters of it that the segment names
     */
    public static DraftContext replay(final ContextSnapshot snapshot, final ProtectedMask mask, final Segment segment) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(segment, "segment");
        final List<String> texts = Tokens.visibleTexts(List.of(segment));
        final List<InjectedTerm> terms = snapshot.glossary().stream()
                .filter(term -> namedIn(term.term(), texts))
                .map(term -> new InjectedTerm(term, InjectedTerms.lines(term, mask)))
                .toList();
        final List<SnapshotRendering> lexicon = snapshot.lexicon().stream()
                .filter(rendering -> namedIn(rendering.term(), texts))
                .toList();
        final List<String> characters = snapshot.characters().stream()
                .filter(line -> namedIn(line.split(" — ", 2)[0], texts))
                .toList();
        final DraftContext context = new DraftContext(
                snapshot.precedingTargets(),
                snapshot.summary(),
                lines(terms, false),
                memoryLines(snapshot.tmHits()),
                lines(terms, true),
                lexiconLines(lexicon),
                characters);
        logReplayed(segment, snapshot, context, terms.size());
        return context;
    }

    private static void logReplayed(
            final Segment segment, final ContextSnapshot snapshot, final DraftContext context, final int terms) {
        log.debug(
                "Replayed context segment={} preceding={} terms={}/{} lexicon={}/{} characters={}/{} "
                        + "suggestedLines={} memoryLines={} summary={}",
                segment.id(),
                context.precedingTargets().size(),
                terms,
                snapshot.glossary().size(),
                context.lexiconLines().size(),
                snapshot.lexicon().size(),
                context.characterLines().size(),
                snapshot.characters().size(),
                context.suggestedLines().size(),
                context.memoryLines().size(),
                snapshot.summary() != null);
    }

    private static boolean namedIn(final String term, final List<String> texts) {
        return texts.stream().anyMatch(text -> TermMatch.isNamedIn(term, text));
    }

    private static String scope(final ContextSnapshot snapshot, final ContextInputs inputs) {
        final long cast = inputs.glossary().stream()
                .filter(entry -> entry.type() == TermType.CHARACTER && entry.gender() != Gender.UNKNOWN)
                .count();
        return "glossary=" + snapshot.glossary().size() + "/"
                + inputs.glossary().size()
                + " lexicon=" + snapshot.lexicon().size() + "/"
                + inputs.lexicon().size()
                + " characters=" + snapshot.characters().size() + "/" + cast;
    }

    /** The prompt lines of the terms whose target is, or is not, an unconfirmed suggestion. */
    private static List<String> lines(final List<InjectedTerm> terms, final boolean suggested) {
        return terms.stream()
                .filter(term -> term.term().suggested() == suggested)
                .flatMap(term -> term.lines().stream())
                .toList();
    }

    private static List<String> lexiconLines(final List<SnapshotRendering> lexicon) {
        return lexicon.stream().map(InjectedLexicon::line).toList();
    }

    private static List<String> memoryLines(final List<SnapshotTmHit> hits) {
        return hits.stream()
                .filter(hit -> hit.kind() != TmHitKind.CONTEXT)
                .map(hit -> hit.source() + " → " + hit.target())
                .toList();
    }

    private static List<String> precedingTexts(final ContextInputs inputs) {
        final List<String> earlier = inputs.earlierMaskedTargets();
        final int from = Math.max(0, earlier.size() - inputs.precedingCount());
        return earlier.subList(from, earlier.size()).stream()
                .map(DisplayText::of)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    private static List<SnapshotTmHit> memoryHits(final TmLookup memory) {
        final List<SnapshotTmHit> hits = new ArrayList<>();
        addHit(hits, TmHitKind.CONTEXT, memory.reuse());
        memory.hints().forEach(entry -> addHit(hits, TmHitKind.EXACT, entry));
        memory.suggestions().forEach(entry -> addHit(hits, TmHitKind.FUZZY, entry));
        return hits;
    }

    private static void addHit(final List<SnapshotTmHit> hits, final TmHitKind kind, @Nullable final TmEntry entry) {
        if (entry == null) {
            return;
        }
        final String target = DisplayText.of(entry.targetInner());
        if (target.isEmpty()) {
            log.debug("Memory entry {} has no text once its tokens are removed; left out", entry.id());
            return;
        }
        hits.add(new SnapshotTmHit(kind, entry.sourceInner(), target));
    }

    private static void logAssembly(
            final Segment segment,
            final ContextSnapshot snapshot,
            final List<String> memoryLines,
            final DraftContext context,
            final String scope) {
        final List<SnapshotTerm> terms = snapshot.glossary();
        final long locked = terms.stream()
                .filter(term -> term.locked() && InjectedTerms.hasTarget(term))
                .count();
        final long noTarget =
                terms.stream().filter(term -> !InjectedTerms.hasTarget(term)).count();
        final long suggested = terms.stream().filter(SnapshotTerm::suggested).count();
        log.debug(
                "Assembled context scoped {} segment={} preceding={} lockedTerms={} unlockedTerms={} suggestedTerms={} "
                        + "noTargetTerms={} characters={} lexicon={} hints={} suggestions={} reuse={} summary={}",
                scope,
                segment.id(),
                snapshot.precedingTargets().size(),
                locked,
                terms.size() - locked - noTarget - suggested,
                suggested,
                noTarget,
                snapshot.characters().size(),
                snapshot.lexicon().size(),
                hitsOfKind(snapshot, TmHitKind.EXACT),
                hitsOfKind(snapshot, TmHitKind.FUZZY),
                hitsOfKind(snapshot, TmHitKind.CONTEXT) > 0,
                snapshot.summary() != null);
        logBlocks(segment, snapshot, memoryLines, context);
    }

    private static void logBlocks(
            final Segment segment,
            final ContextSnapshot snapshot,
            final List<String> memoryLines,
            final DraftContext context) {
        if (log.isTraceEnabled()) {
            log.trace(
                    "Context blocks segment={} summary={} terms={} suggested={} memory={} preceding={}",
                    segment.id(),
                    snapshot.summary(),
                    context.glossaryLines(),
                    context.suggestedLines(),
                    memoryLines,
                    context.precedingTargets());
        }
    }

    private static long hitsOfKind(final ContextSnapshot snapshot, final TmHitKind kind) {
        return snapshot.tmHits().stream().filter(hit -> hit.kind() == kind).count();
    }
}
