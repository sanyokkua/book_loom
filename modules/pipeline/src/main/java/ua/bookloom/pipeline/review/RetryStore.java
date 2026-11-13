package ua.bookloom.pipeline.review;

import java.util.Objects;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.review.RetryRequests.Retried;
import ua.bookloom.pipeline.run.OutcomeRecords;

/**
 * How a desk retry's decision is stored: a pass is ACCEPTED and reviewed, a failure on a FLAGGED segment stays FLAGGED
 * with the new findings, and a failure never downgrades an ACCEPTED segment. Every stored record keeps the snapshot of
 * the context the retry sent.
 */
@Slf4j
@RequiredArgsConstructor
final class RetryStore {

    private final SegmentRepository segments;

    /**
     * Stores the retry's decision on the record it was drafted from.
     *
     * @param before the non-null record as it read when the retry began
     * @param retried the non-null decided draft and its snapshot
     * @return the stored record, or for a failing ACCEPTED segment its unchanged record carrying the new findings; or
     *     the store's error
     */
    Result<SegmentRecord> store(final SegmentRecord before, final Retried retried) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(retried, "retried");
        final SegmentOutcome outcome = retried.outcome();
        final ContextSnapshot snapshot = retried.snapshot();
        if (log.isTraceEnabled()) {
            log.trace("retry segment={} target={}", before.segmentId(), outcome.machineTarget());
        }
        if (outcome.status() == SegmentStatus.ACCEPTED) {
            return write(
                    before,
                    current ->
                            OutcomeRecords.decided(current, outcome, snapshot).withReviewed(true));
        }
        if (before.status() == SegmentStatus.ACCEPTED) {
            final SegmentRecord answer = before.withFindings(
                    OutcomeRecords.decided(before, outcome, snapshot).findings());
            log.debug(
                    "retry failed on an accepted segment={}; stored record kept unchanged findings={}",
                    before.segmentId(),
                    answer.findings().size());
            logOutcome(answer);
            return Result.ok(answer);
        }
        return write(before, current -> keepingMachineTarget(current, outcome, snapshot));
    }

    // A retry whose reply never passed the hard gates has no target of its own; the earlier one is kept rather than
    // lost, so the person can still accept it.
    private static SegmentRecord keepingMachineTarget(
            final SegmentRecord current, final SegmentOutcome outcome, final ContextSnapshot snapshot) {
        final SegmentRecord decided =
                OutcomeRecords.decided(current, outcome, snapshot).withReviewed(false);
        log.debug(
                "retry: flagged segment={} newMachineTarget={}", current.segmentId(), outcome.machineTarget() != null);
        return outcome.machineTarget() == null
                ? decided.withMachineTarget(current.machineTarget(), current.maskedMachineTarget())
                : decided;
    }

    private Result<SegmentRecord> write(final SegmentRecord before, final UnaryOperator<SegmentRecord> change) {
        final Result<SegmentRecord> updated = segments.update(before.projectId(), before.segmentId(), change);
        final SegmentRecord after = updated.data();
        if (after != null) {
            logOutcome(after);
        }
        return updated;
    }

    private static void logOutcome(final SegmentRecord record) {
        log.info("retry segment={} status={} reviewed={}", record.segmentId(), record.status(), record.reviewed());
    }
}
