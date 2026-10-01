package ua.bookloom.pipeline;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.run.PauseDecider;
import ua.bookloom.pipeline.run.PendingCommit;
import ua.bookloom.pipeline.run.RunBoundaries;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunRecorder;

/**
 * The job's answer at each place its run may pause or stop: it asks the control, commits the decided prefix before it
 * waits, announces the pause and the resume — naming the segment a review pause is about — and records both, so the
 * chunk runner never touches the control itself.
 */
@Slf4j
final class JobBoundaries implements RunBoundaries {

    private final JobControl control;
    private final PendingCommit pending;
    private final RunRecorder recorder;
    private final Consumer<JobEvent> emit;
    private final SegmentRepository segments;
    private final String projectId;
    private final AtomicBoolean skipRequested;

    /**
     * Creates the boundaries of one run.
     *
     * @param skipRequested set by the job's skip action while paused, cleared as each pause begins and taken once
     */
    JobBoundaries(
            final JobControl control,
            final PendingCommit pending,
            final RunRecorder recorder,
            final Consumer<JobEvent> emit,
            final SegmentRepository segments,
            final String projectId,
            final AtomicBoolean skipRequested) {
        this.control = Objects.requireNonNull(control, "control");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.emit = Objects.requireNonNull(emit, "emit");
        this.segments = Objects.requireNonNull(segments, "segments");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.skipRequested = Objects.requireNonNull(skipRequested, "skipRequested");
    }

    @Override
    public boolean takeSkipRequest() {
        final boolean taken = skipRequested.getAndSet(false);
        log.debug("Took the skip request taken={}", taken);
        return taken;
    }

    @Override
    public Optional<RunEnd> afterDecision(final Decision decision, final JobProgress progress) {
        Objects.requireNonNull(decision, "decision");
        forgetSkip();
        final BoundaryDecision answer =
                control.boundary(decision.flagged(), decision.endsSection(), decision.endsRun());
        final PauseReason reason = answer.pauseReason();
        if (reason != null && PauseDecider.namesSegment(reason)) {
            JobPauseLogger.reviewPause(reason, decision.segmentId(), progress);
            return pause(reason, null, decision.segmentId(), progress);
        }
        return honor(answer, progress);
    }

    @Override
    public Optional<RunEnd> afterAbortedCall(final JobProgress progress) {
        forgetSkip();
        return honor(control.abortedCallBoundary(), progress);
    }

    @Override
    public Optional<RunEnd> afterRoutedError(final AppError error, final JobProgress progress) {
        Objects.requireNonNull(error, "error");
        forgetSkip();
        final BoundaryDecision decision = control.failureBoundary(error);
        if (decision.cancelled()) {
            return Optional.of(new RunEnd(JobState.CANCELLED, null));
        }
        final PauseReason reason = decision.pauseReason();
        if (reason == null) {
            log.debug("Ending the run on a provider error code={}: pausing on an error is off", error.code());
            return Optional.of(new RunEnd(JobState.FAILED, error));
        }
        JobPauseLogger.recoveryPause(error, reason, progress);
        return pause(reason, error, null, progress);
    }

    // Cleared before a boundary can pause, so only a skip asked during the pause that follows may skip anything.
    private void forgetSkip() {
        skipRequested.set(false);
    }

    private Optional<RunEnd> honor(final BoundaryDecision decision, final JobProgress progress) {
        if (decision.cancelled()) {
            return Optional.of(new RunEnd(JobState.CANCELLED, null));
        }
        final PauseReason reason = decision.pauseReason();
        return reason == null ? Optional.empty() : pause(reason, null, null, progress);
    }

    private Optional<RunEnd> pause(
            final PauseReason reason,
            @Nullable final AppError error,
            @Nullable final String segmentId,
            final JobProgress progress) {
        final Result<Integer> flushed = pending.flush();
        if (flushed.isErr()) {
            return Optional.of(new RunEnd(JobState.FAILED, Objects.requireNonNull(flushed.error(), "error")));
        }
        recorder.paused();
        emit.accept(new Paused(reason, error, progress, segmentId));
        if (control.awaitPause() == PauseWait.CANCELLED) {
            return Optional.of(new RunEnd(JobState.CANCELLED, null));
        }
        recorder.resumed();
        emit.accept(new Resumed(progress));
        return segmentId == null ? Optional.empty() : reread(segmentId);
    }

    /**
     * Reads the paused segment's stored decision again, which may now be the person's edit: the rest of the run
     * reads every earlier target from the store, and this is the record they will find.
     */
    private Optional<RunEnd> reread(final String segmentId) {
        final Result<Optional<SegmentRecord>> read = segments.find(projectId, segmentId);
        if (read.isErr()) {
            return Optional.of(new RunEnd(JobState.FAILED, Objects.requireNonNull(read.error(), "error")));
        }
        final Optional<SegmentRecord> record = Objects.requireNonNull(read.data(), "record");
        log.debug(
                "Re-read the paused segment on resume segmentId={} status={} edited={}",
                segmentId,
                record.map(SegmentRecord::status).orElse(null),
                record.map(stored -> stored.userTarget() != null).orElse(false));
        return Optional.empty();
    }
}
