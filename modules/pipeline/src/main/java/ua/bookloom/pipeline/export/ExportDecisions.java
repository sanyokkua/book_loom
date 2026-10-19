package ua.bookloom.pipeline.export;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;

/** The decided targets copied onto the freshly opened source, and taken back off the segments written in source. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportDecisions {

    /**
     * Copies each decided segment's status and target onto the fresh book.
     *
     * @param fresh the non-null book re-opened from the source for this export
     * @param decided the non-null book carrying the decisions
     * @return the fresh book with every decision applied
     */
    static Document apply(final Document fresh, final Document decided) {
        final Map<String, Segment> decisions = decided.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .collect(Collectors.toMap(
                        Segment::id, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        final List<Unit> units = fresh.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> copyDecision(segment, decisions.get(segment.id())))
                        .toList()))
                .toList();
        final Document applied = fresh.withUnits(units);
        log.debug(
                "Applied export decisions: accepted {}, flagged {}",
                countStatus(applied, SegmentStatus.ACCEPTED),
                countStatus(applied, SegmentStatus.FLAGGED));
        return applied;
    }

    /**
     * The same book with each named segment's source written as its target. Not a cleared target: the writer leaves a
     * segment with no target as its open node holds it, and that node already holds the translation written first.
     *
     * @param applied the non-null book as it was written
     * @param segmentIds the non-null ids of the segments to write in their source
     * @return the book to write again
     */
    static Document inSource(final Document applied, final List<String> segmentIds) {
        log.debug("Writing segments in their source ids={}", segmentIds);
        final List<Unit> units = applied.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> segmentIds.contains(segment.id())
                                ? segment.withDecision(segment.status(), segment.sourceInner())
                                : segment)
                        .toList()))
                .toList();
        return applied.withUnits(units);
    }

    /**
     * Each named segment's own masked form, which its source written as its target is checked against.
     *
     * @param book the non-null book holding the segments
     * @param segmentIds the non-null ids
     * @return each id mapped to its segment's masked form
     */
    static Map<String, String> sourceMasks(final Document book, final List<String> segmentIds) {
        return book.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segmentIds.contains(segment.id()))
                .collect(Collectors.toMap(Segment::id, Segment::masked, (first, ignored) -> first));
    }

    private static Segment copyDecision(final Segment fresh, @Nullable final Segment decision) {
        return decision == null ? fresh : fresh.withDecision(decision.status(), decision.targetInner());
    }

    private static long countStatus(final Document document, final SegmentStatus status) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.status() == status)
                .count();
    }
}
