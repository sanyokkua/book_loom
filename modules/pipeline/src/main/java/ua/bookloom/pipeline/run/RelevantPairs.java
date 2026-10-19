package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * Chooses the accepted earlier pairs that share the most of a batch's names and recurring terms, through
 * {@link BookIndex#sharing}, with no model call. Only a segment the run or the person accepted counts, so a pair shown
 * is a rendering the book keeps; a segment the person revised shows the person's text.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class RelevantPairs {

    /** How many relevant pairs a batch shows at most. */
    static final int LIMIT = 2;

    // The index is asked for more ordinals than the block holds, because flagged, pending and already shown segments
    // are passed over.
    private static final int ASKED = 6 * LIMIT;

    private final SegmentRepository segments;
    private final String projectId;
    private final PendingCommit pending;
    private final BookIndex index;
    private final Map<String, Segment> byId = new HashMap<>();

    /**
     * Creates the selection of one run.
     *
     * @param segments the non-null stored records
     * @param projectId the non-null project the run translates
     * @param pending the non-null decisions of the run not yet stored, which are newer than the stored records
     * @param index the non-null index of the book the run started with
     * @param document the non-null book the index was built from, whose segments the pairs show
     */
    RelevantPairs(
            final SegmentRepository segments,
            final String projectId,
            final PendingCommit pending,
            final BookIndex index,
            final Document document) {
        this.segments = Objects.requireNonNull(segments, "segments");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.index = Objects.requireNonNull(index, "index");
        Objects.requireNonNull(document, "document");
        for (final Unit unit : document.units()) {
            unit.segments().forEach(segment -> byId.put(segment.id(), segment));
        }
    }

    /**
     * The relevant pairs of a batch.
     *
     * @param terms the non-null glossary names and recurring terms the batch's items name
     * @param first the non-null first segment of the batch: only segments before it count
     * @param preceding the non-null batch's preceding pairs, which are not repeated
     * @return up to {@link #LIMIT} pairs, best first (most shared terms, ties to the nearer segment); empty when no
     *     accepted segment shares a term; or the repository's error
     */
    Result<List<EarlierPair>> select(final List<String> terms, final Segment first, final List<EarlierPair> preceding) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(preceding, "preceding");
        final OptionalInt ordinal = index.ordinalOf(first.id());
        if (terms.isEmpty() || ordinal.isEmpty()) {
            log.debug(
                    "No relevant pairs segmentId={} terms={} indexed={}",
                    first.id(),
                    terms.size(),
                    ordinal.isPresent());
            return Result.ok(List.of());
        }
        final List<Integer> asked = index.sharing(terms, ordinal.getAsInt(), ASKED);
        final Set<String> shown =
                preceding.stream().map(pair -> pair.segment().id()).collect(Collectors.toSet());
        log.debug("Relevant pairs segmentId={} sharing ordinals={}", first.id(), asked);
        return accepted(asked, shown);
    }

    private Result<List<EarlierPair>> accepted(final List<Integer> asked, final Set<String> shown) {
        final List<EarlierPair> chosen = new ArrayList<>();
        for (final int candidate : asked) {
            if (chosen.size() == LIMIT) {
                break;
            }
            final String id = index.segment(candidate).segmentId();
            if (shown.contains(id)) {
                log.debug("Relevant candidate {} is already a preceding pair", id);
                continue;
            }
            final Result<Optional<String>> target = acceptedTarget(id);
            if (target.isErr()) {
                return Result.err(Objects.requireNonNull(target.error(), "error"));
            }
            Objects.requireNonNull(target.data(), "target")
                    .ifPresent(found -> chosen.add(new EarlierPair(segmentOf(id), found)));
        }
        log.debug(
                "Relevant pairs chosen ids={}",
                chosen.stream().map(pair -> pair.segment().id()).toList());
        return Result.ok(List.copyOf(chosen));
    }

    private Segment segmentOf(final String segmentId) {
        return Objects.requireNonNull(byId.get(segmentId), () -> "no segment " + segmentId);
    }

    // A decision the run holds but has not stored is newer than the stored record, and a flagged one is no pair.
    private Result<Optional<String>> acceptedTarget(final String segmentId) {
        final Optional<SegmentRecord> held = pending.held(segmentId);
        if (held.isPresent()) {
            return Result.ok(acceptedIn(held.get()));
        }
        return segments.find(projectId, segmentId).map(found -> found.flatMap(RelevantPairs::acceptedIn));
    }

    private static Optional<String> acceptedIn(final SegmentRecord record) {
        final boolean isAccepted =
                record.status() == SegmentStatus.ACCEPTED || record.status() == SegmentStatus.REVISED;
        if (!isAccepted) {
            log.debug("Relevant candidate {} is {}: not shown", record.segmentId(), record.status());
            return Optional.empty();
        }
        return PrecedingTargets.targetOf(record);
    }
}
