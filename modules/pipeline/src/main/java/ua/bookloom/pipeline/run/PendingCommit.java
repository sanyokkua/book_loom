package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The records a run has decided and not yet stored, and the ids it has already stored.
 *
 * <p>The checkpoint replaces whole records, so sending a stored id again would put the machine's record over an
 * edit the person saved while the run was paused. A record whose id was already committed is therefore never
 * committed a second time.
 *
 * <p>Used from the job thread only, which is why the state is plain collections.
 */
@Slf4j
public final class PendingCommit {

    private final CheckpointPort checkpoint;
    private final String projectId;
    private final List<SegmentRecord> decided = new ArrayList<>();
    private final Set<String> committed = new HashSet<>();

    /**
     * Creates the pending set of one run.
     *
     * @param checkpoint the non-null port decisions are committed through
     * @param projectId the non-null id of the project the run decides
     */
    public PendingCommit(final CheckpointPort checkpoint, final String projectId) {
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    /**
     * Holds a decided record until the next flush.
     *
     * @param record the non-null decided record
     */
    public void decided(final SegmentRecord record) {
        Objects.requireNonNull(record, "record");
        decided.add(record);
    }

    /**
     * Finds a record decided since the last flush, so a later draft of the same chunk reads the decision the run has
     * made but not yet stored.
     *
     * @param segmentId the non-null segment id
     * @return the decided record if one is held, or empty when none is — never held again once flushed
     */
    public Optional<SegmentRecord> held(final String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        return decided.stream()
                .filter(record -> record.segmentId().equals(segmentId))
                .reduce((earlier, later) -> later);
    }

    /**
     * Commits everything decided since the last flush, in the order it was decided, except a record whose id an
     * earlier flush already committed.
     *
     * <p>The records leave the pending set before the port is called, so a commit that fails is not retried by the
     * flush that ends the run: a run that cannot store its decisions ends, and must not fail a second time doing so.
     *
     * @return the number of items the commit applied, {@code 0} when nothing was due, or the port's failure
     */
    public Result<Integer> flush() {
        final List<SegmentRecord> taken = List.copyOf(decided);
        decided.clear();
        final List<SegmentRecord> due = taken.stream()
                .filter(record -> !committed.contains(record.segmentId()))
                .toList();
        log.debug(
                "Flushing decisions projectId={} decided={} withheldAsCommitted={}",
                projectId,
                due.size(),
                taken.stream()
                        .map(SegmentRecord::segmentId)
                        .filter(committed::contains)
                        .toList());
        if (due.isEmpty()) {
            return Result.ok(0);
        }
        final Result<Integer> result =
                checkpoint.commit(new ChunkCommit(projectId, due, List.of(), List.of(), List.of()));
        if (result.isOk()) {
            due.forEach(record -> committed.add(record.segmentId()));
        }
        return result;
    }
}
