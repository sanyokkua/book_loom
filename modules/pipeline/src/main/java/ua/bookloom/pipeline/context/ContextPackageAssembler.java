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
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.SnapshotTmHit.TmHitKind;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.chunk.Chunk;
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
     * @param chunk the chunk holding the segment; its segments decide which glossary terms occur
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
        final List<InjectedTerm> terms = InjectedTerms.select(chunk, mask, inputs.glossary());
        final List<String> preceding = precedingTexts(inputs);
        final List<SnapshotTmHit> hits = memoryHits(memory);
        final List<String> glossaryLines =
                terms.stream().flatMap(term -> term.lines().stream()).toList();
        final List<String> memoryLines = memoryLines(hits);
        final DraftContext context = new DraftContext(preceding, inputs.summary(), glossaryLines, memoryLines);
        final ContextSnapshot snapshot = new ContextSnapshot(
                preceding,
                terms.stream().map(InjectedTerm::term).toList(),
                hits,
                inputs.summary(),
                inputs.styleSheet().text());
        logAssembly(segment, snapshot, memoryLines, context);
        return new ContextPackage(context, snapshot);
    }

    /**
     * Rebuilds a draft's context from the texts its snapshot recorded, so a retry is shown what the first draft was
     * shown even after the glossary, the memory or the summary changed. Nothing is looked up.
     *
     * @param snapshot the non-null snapshot the first draft's record stores
     * @param mask the segment's non-null mask, built from the snapshot's terms, which decides how a locked term is
     *     described
     * @return the same preceding targets, term lines, memory lines and summary the snapshot was assembled with
     */
    public static DraftContext replay(final ContextSnapshot snapshot, final ProtectedMask mask) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(mask, "mask");
        final List<String> glossaryLines = snapshot.glossary().stream()
                .flatMap(term -> InjectedTerms.lines(term, mask).stream())
                .toList();
        final DraftContext context = new DraftContext(
                snapshot.precedingTargets(), snapshot.summary(), glossaryLines, memoryLines(snapshot.tmHits()));
        log.debug(
                "Replayed context preceding={} terms={} glossaryLines={} memoryLines={} summary={}",
                context.precedingTargets().size(),
                snapshot.glossary().size(),
                glossaryLines.size(),
                context.memoryLines().size(),
                snapshot.summary() != null);
        return context;
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
            final DraftContext context) {
        final List<SnapshotTerm> terms = snapshot.glossary();
        final long locked = terms.stream()
                .filter(term -> term.locked() && InjectedTerms.hasTarget(term))
                .count();
        final long noTarget =
                terms.stream().filter(term -> !InjectedTerms.hasTarget(term)).count();
        log.debug(
                "Assembled context segment={} preceding={} lockedTerms={} unlockedTerms={} noTargetTerms={} "
                        + "hints={} suggestions={} reuse={} summary={}",
                segment.id(),
                snapshot.precedingTargets().size(),
                locked,
                terms.size() - locked - noTarget,
                noTarget,
                hitsOfKind(snapshot, TmHitKind.EXACT),
                hitsOfKind(snapshot, TmHitKind.FUZZY),
                hitsOfKind(snapshot, TmHitKind.CONTEXT) > 0,
                snapshot.summary() != null);
        if (log.isTraceEnabled()) {
            log.trace(
                    "Context blocks segment={} summary={} terms={} memory={} preceding={}",
                    segment.id(),
                    snapshot.summary(),
                    context.glossaryLines(),
                    memoryLines,
                    context.precedingTargets());
        }
    }

    private static long hitsOfKind(final ContextSnapshot snapshot, final TmHitKind kind) {
        return snapshot.tmHits().stream().filter(hit -> hit.kind() == kind).count();
    }
}
