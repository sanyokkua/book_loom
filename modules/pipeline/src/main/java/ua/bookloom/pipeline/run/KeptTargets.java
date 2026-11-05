package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.context.AcceptedTargets;

/**
 * Reads what a window's previous pairs show of the segments before it (design D8). A segment decided and already
 * committed is read from the repository, so an edit the person saved during a pause is what the next draft sees; one
 * decided but not yet committed is read from the pending commit; one drafted and undecided in the same chunk gives its
 * source only.
 */
@Slf4j
final class KeptTargets {

    private final SegmentRepository segments;
    private final String projectId;
    private final PendingCommit pending;

    KeptTargets(final SegmentRepository segments, final String projectId, final PendingCommit pending) {
        this.segments = Objects.requireNonNull(segments, "segments");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.pending = Objects.requireNonNull(pending, "pending");
    }

    /**
     * The reader of a segment's target, shown only when it was kept — accepted, revised or the person's edit.
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
}
