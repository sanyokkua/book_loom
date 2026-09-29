package ua.bookloom.pipeline;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.pipeline.run.PendingCommit;
import ua.bookloom.pipeline.run.RunBoundaries;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunRecorder;

/**
 * The job's answer at each place its run may pause or stop: it asks the control, commits the decided prefix before it
 * waits, announces the pause and the resume, and records both, so the chunk runner never touches the control itself.
 */
@Slf4j
final class JobBoundaries implements RunBoundaries {

    private final JobControl control;
    private final PendingCommit pending;
    private final RunRecorder recorder;
    private final Consumer<JobEvent> emit;

    JobBoundaries(
            final JobControl control,
            final PendingCommit pending,
            final RunRecorder recorder,
            final Consumer<JobEvent> emit) {
        this.control = Objects.requireNonNull(control, "control");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.emit = Objects.requireNonNull(emit, "emit");
    }

    @Override
    public Optional<RunEnd> afterDecision(
            final boolean endsSection, final boolean endsRun, final JobProgress progress) {
        return honor(control.boundary(true, endsSection, endsRun), progress);
    }

    @Override
    public Optional<RunEnd> afterAbortedCall(final JobProgress progress) {
        return honor(control.abortedCallBoundary(), progress);
    }

    @Override
    public Optional<RunEnd> afterRoutedError(final AppError error, final JobProgress progress) {
        Objects.requireNonNull(error, "error");
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
        return pause(reason, error, progress);
    }

    private Optional<RunEnd> honor(final BoundaryDecision decision, final JobProgress progress) {
        if (decision.cancelled()) {
            return Optional.of(new RunEnd(JobState.CANCELLED, null));
        }
        final PauseReason reason = decision.pauseReason();
        return reason == null ? Optional.empty() : pause(reason, null, progress);
    }

    private Optional<RunEnd> pause(
            final PauseReason reason, @Nullable final AppError error, final JobProgress progress) {
        final Result<Integer> flushed = pending.flush();
        if (flushed.isErr()) {
            return Optional.of(new RunEnd(JobState.FAILED, Objects.requireNonNull(flushed.error(), "error")));
        }
        recorder.paused();
        emit.accept(new Paused(reason, error, progress));
        if (control.awaitPause() == PauseWait.CANCELLED) {
            return Optional.of(new RunEnd(JobState.CANCELLED, null));
        }
        recorder.resumed();
        emit.accept(new Resumed(progress));
        return Optional.empty();
    }
}
