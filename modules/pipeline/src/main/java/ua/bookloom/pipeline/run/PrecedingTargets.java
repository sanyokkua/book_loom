package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.context.AcceptedTargets;

/**
 * Reads the masked targets of the segments before a draft in its unit. A segment decided and already committed is
 * read from the repository, so an edit the person saved during a pause is what the next draft sees; one decided but
 * not yet committed is read from the pending commit; one drafted and undecided in the same chunk from its draft.
 */
@Slf4j
final class PrecedingTargets {

    private final SegmentRepository segments;
    private final String projectId;
    private final PendingCommit pending;

    PrecedingTargets(final SegmentRepository segments, final String projectId, final PendingCommit pending) {
        this.segments = Objects.requireNonNull(segments, "segments");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.pending = Objects.requireNonNull(pending, "pending");
    }

    /**
     * The masked targets of the last {@code count} earlier segments of the unit that have one, in document order. A
     * segment with no target — flagged with none, or pending — is passed over.
     *
     * @return never null; empty at a unit's start, or the repository's error
     */
    Result<List<String>> earlierMaskedTargets(
            final List<Segment> unitSegments, final Segment segment, final int count, final ChunkDrafts drafts) {
        return earlier(unitSegments, segment, count, drafts)
                .map(found -> found.stream().map(EarlierPair::target).toList());
    }

    /**
     * The last {@code count} earlier segments of the unit that have a target, each with it, in document order: what a
     * batch shows as its previous pairs.
     *
     * @return never null; empty at a unit's start, or the repository's error
     */
    Result<List<EarlierPair>> earlierPairs(
            final List<Segment> unitSegments, final Segment segment, final int count, final ChunkDrafts drafts) {
        return earlier(unitSegments, segment, count, drafts);
    }

    /**
     * What a window's previous pairs read (design D8): a segment's target only when it was kept — accepted, revised
     * or the person's edit — read from the pending commit before the store; a draft of this chunk still undecided
     * gives its source only.
     *
     * @param drafts the non-null drafts of the chunk being built
     * @return the reader; never null
     */
    AcceptedTargets accepted(final ChunkDrafts drafts) {
        Objects.requireNonNull(drafts, "drafts");
        return segmentId -> acceptedTarget(segmentId, drafts);
    }

    private Result<Optional<String>> acceptedTarget(final String segmentId, final ChunkDrafts drafts) {
        final Optional<SegmentRecord> held = pending.held(segmentId);
        final Result<Optional<String>> target;
        final String from;
        if (held.isPresent()) {
            from = "pending";
            target = Result.ok(AcceptedTargets.of(held.get()));
        } else if (drafts.isUndecided(segmentId)) {
            from = "undecided";
            target = Result.ok(Optional.empty());
        } else {
            from = "stored";
            target = segments.find(projectId, segmentId).map(found -> found.flatMap(AcceptedTargets::of));
        }
        log.debug(
                "Accepted target segmentId={} from={} shown={}",
                segmentId,
                from,
                target.isOk() && Objects.requireNonNull(target.data(), "data").isPresent());
        return target;
    }

    private Result<List<EarlierPair>> earlier(
            final List<Segment> unitSegments, final Segment segment, final int count, final ChunkDrafts drafts) {
        final List<EarlierPair> earlier = new ArrayList<>();
        for (int index = positionOf(unitSegments, segment) - 1; index >= 0 && earlier.size() < count; index--) {
            final Segment before = unitSegments.get(index);
            final Result<Optional<String>> target = maskedTargetOf(before.id(), drafts);
            if (target.isErr()) {
                return Result.err(Objects.requireNonNull(target.error(), "error"));
            }
            Objects.requireNonNull(target.data(), "target")
                    .ifPresent(found -> earlier.addFirst(new EarlierPair(before, found)));
        }
        log.debug("Read preceding targets segmentId={} wanted={} found={}", segment.id(), count, earlier.size());
        return Result.ok(List.copyOf(earlier));
    }

    private Result<Optional<String>> maskedTargetOf(final String segmentId, final ChunkDrafts drafts) {
        final Optional<SegmentRecord> held = pending.held(segmentId);
        if (held.isPresent()) {
            return Result.ok(targetOf(held.get()));
        }
        if (drafts.isUndecided(segmentId)) {
            return Result.ok(present(drafts.maskedTarget(segmentId)));
        }
        return segments.find(projectId, segmentId).map(found -> found.flatMap(PrecedingTargets::targetOf));
    }

    /** The record's masked target as the person last left it, else the machine's; empty when it has none to show. */
    static Optional<String> targetOf(final SegmentRecord record) {
        return present(effective(record));
    }

    private static @Nullable String effective(final SegmentRecord record) {
        return record.userTarget() == null ? record.maskedMachineTarget() : record.maskedUserTarget();
    }

    private static Optional<String> present(@Nullable final String target) {
        return target == null || target.isBlank() ? Optional.empty() : Optional.of(target);
    }

    private static int positionOf(final List<Segment> unitSegments, final Segment segment) {
        for (int index = 0; index < unitSegments.size(); index++) {
            if (unitSegments.get(index).id().equals(segment.id())) {
                return index;
            }
        }
        return 0;
    }
}
