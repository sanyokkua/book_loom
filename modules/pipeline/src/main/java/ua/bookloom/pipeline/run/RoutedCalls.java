package ua.bookloom.pipeline.run;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;

/**
 * Makes a run's calls until they answer, routing each error they answer by design D3: a stop or a pause aborting a
 * call, and a provider error the run pauses on, are answered by the job's boundaries, which either end the run or send
 * the call again once the person resumes. A draft, the reviewer and a unit's summary are all routed here, so they pause
 * and fail alike.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class RoutedCalls {

    // The answer a step skipped while its call waited is flagged with: the person left it, so it has no other error.
    private static final AppError SKIPPED = AppError.of(
            ErrorCode.cancelled, "Segment skipped", "The segment was skipped while its model call was waiting.");

    private final RunBoundaries boundaries;
    // The pauses each give-up-able step caused so far, by step and recovery kind, until the step answers or is flagged:
    // only steps that are still failing are held, so a night's run does not keep one entry per segment. Job thread
    // only.
    private final Map<String, Integer> pauses = new HashMap<>();

    /**
     * Creates the router of one run.
     *
     * @param boundaries the non-null job boundaries that answer a routed error
     */
    RoutedCalls(final RunBoundaries boundaries) {
        this.boundaries = Objects.requireNonNull(boundaries, "boundaries");
    }

    /**
     * Makes a call until it answers or the run ends.
     *
     * @param work the run's work list, whose progress a pause reports
     * @param segmentId the segment the call is for, put into the log context, or {@code null} for a call of no single
     *     segment
     * @param call the call
     * @return the answer, or how the run ended while the call was routed
     */
    <T> Step<T> untilAnswered(final WorkList work, @Nullable final String segmentId, final Supplier<Result<T>> call) {
        return untilAnswered(work::currentTranslationProgress, segmentId, call);
    }

    /**
     * Makes a call until it answers or the run ends, a pause reporting the progress {@code progress} gives.
     *
     * @param progress the progress a pause reports, read when the pause happens
     * @param segmentId the segment the call is for, or {@code null} for a call of no single segment
     * @param call the call
     * @return the answer, or how the run ended while the call was routed
     */
    <T> Step<T> untilAnswered(
            final Supplier<JobProgress> progress, @Nullable final String segmentId, final Supplier<Result<T>> call) {
        return route(progress, segmentId, call, null);
    }

    /**
     * Makes a call until it answers, the run ends, or the run gives up on it: after as many pauses for the same step
     * as its kind of recovery allows ({@link PauseDecider.Recovery#pausesBeforeFlagging()}), or when the person skips it from a pause, the step's error is turned into a flagged
     * answer by {@code flag} and the run goes on ({@code specs/translation-pipeline/spec.md} "Give up on a step that
     * keeps failing").
     *
     * @param work the run's work list, whose progress a pause reports
     * @param segmentId the segment the call is for, put into the log context, or {@code null} for a call of no single
     *     segment
     * @param step names the step for the pause count and for the person: its kind and the segment it is for (a
     *     chunk's reviewer call is named by the chunk's first segment)
     * @param call the call
     * @param flag turns the error the step last answered into its flagged answer
     * @return the answer, the flagged answer, or how the run ended while the call was routed
     */
    <T> Step<T> untilAnsweredOrFlagged(
            final WorkList work,
            @Nullable final String segmentId,
            final StepName step,
            final Supplier<Result<T>> call,
            final Function<AppError, T> flag) {
        return untilAnsweredOrFlagged(work::currentTranslationProgress, segmentId, step, call, flag);
    }

    /** As {@link #untilAnsweredOrFlagged(WorkList, String, StepName, Supplier, Function)}, a pause reporting
     * the progress {@code progress} gives. */
    <T> Step<T> untilAnsweredOrFlagged(
            final Supplier<JobProgress> progress,
            @Nullable final String segmentId,
            final StepName step,
            final Supplier<Result<T>> call,
            final Function<AppError, T> flag) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(flag, "flag");
        return route(progress, segmentId, call, new GiveUp<>(step, flag));
    }

    private <T> Step<T> route(
            final Supplier<JobProgress> progress,
            @Nullable final String segmentId,
            final Supplier<Result<T>> call,
            @Nullable final GiveUp<T> giveUp) {
        // The pauses a call no step names has caused, by kind: such a call is never flagged, so past its budget it
        // waits for the person instead of recovering by itself again.
        final Map<PauseDecider.Recovery, Integer> unnamedPauses = new EnumMap<>(PauseDecider.Recovery.class);
        while (true) {
            final Result<T> result = withSegment(segmentId, call);
            if (result.isOk()) {
                boundaries.callAnswered();
                forget(giveUp);
                return new Step.Done<>(Objects.requireNonNull(result.data(), "data"));
            }
            final AppError error = Objects.requireNonNull(result.error(), "error");
            final Optional<Step<T>> settled = afterError(error, progress.get(), giveUp, unnamedPauses);
            if (settled.isPresent()) {
                forget(giveUp);
                return settled.get();
            }
            log.debug("Making the call again segmentId={} after code={}", segmentId, error.code());
        }
    }

    /**
     * How many steps' pause counts are held: the steps that paused the run and have neither answered nor been flagged.
     *
     * @return never negative
     */
    int heldPauseCounts() {
        return pauses.size();
    }

    // A step that answered, was flagged or ended the run is never counted again under this name in this run.
    private void forget(@Nullable final GiveUp<?> giveUp) {
        if (giveUp != null && !pauses.isEmpty()) {
            final String prefix = giveUp.step().key() + "/";
            pauses.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    /**
     * The end of a run a storage error stopped.
     *
     * @param error the non-null error
     * @return the failed end carrying it
     */
    static RunEnd failedBy(final AppError error) {
        return new RunEnd(JobState.FAILED, error);
    }

    private <T> Optional<Step<T>> afterError(
            final AppError error,
            final JobProgress progress,
            @Nullable final GiveUp<T> giveUp,
            final Map<PauseDecider.Recovery, Integer> unnamedPauses) {
        return switch (PauseDecider.route(error.code())) {
            case CANCELLED -> afterAbortedCall(progress, giveUp);
            case PAUSE_OR_FAIL -> afterProviderError(error, progress, giveUp, unnamedPauses);
            case FLAG_AT_ONCE, FAIL -> Optional.of(new Step.Stopped<>(failedBy(endingError(error))));
        };
    }

    // A call the person paused while it hung may be skipped from that pause too, so a stalled step can be left behind.
    private <T> Optional<Step<T>> afterAbortedCall(final JobProgress progress, @Nullable final GiveUp<T> giveUp) {
        final Optional<RunEnd> end = boundaries.afterAbortedCall(progress);
        if (end.isPresent()) {
            return Optional.of(new Step.Stopped<>(end.get()));
        }
        if (giveUp != null && boundaries.takeSkipRequest()) {
            log.info("Skipping step={} as asked from a pause while its call waited; it is flagged", giveUp.step());
            return Optional.of(new Step.Done<>(giveUp.flag().apply(SKIPPED)));
        }
        return Optional.empty();
    }

    // The budget is checked before the pause, so the failure past it flags the step instead of pausing again. Each
    // kind of recovery has its own count, so an outage never spends what a stalled call may use.
    private <T> Optional<Step<T>> afterProviderError(
            final AppError error,
            final JobProgress progress,
            @Nullable final GiveUp<T> giveUp,
            final Map<PauseDecider.Recovery, Integer> unnamedPauses) {
        final PauseDecider.Recovery recovery = PauseDecider.recovery(error.code());
        final int budget = recovery.pausesBeforeFlagging();
        final int pausedBefore = giveUp == null
                ? unnamedPauses.getOrDefault(recovery, 0)
                : pauses.getOrDefault(countKey(giveUp, recovery), 0);
        if (giveUp != null && pausedBefore >= budget) {
            log.warn(
                    "Flagging step={} after {} pauses for it recovery={} code={}; the run goes on",
                    giveUp.step(),
                    budget,
                    recovery,
                    error.code());
            return Optional.of(new Step.Done<>(giveUp.flag().apply(error)));
        }
        final Optional<RunEnd> end =
                boundaries.afterRoutedError(error, progress, failing(error, recovery, pausedBefore, giveUp));
        if (end.isPresent()) {
            return Optional.of(new Step.Stopped<>(end.get()));
        }
        if (giveUp == null) {
            unnamedPauses.merge(recovery, 1, Integer::sum);
            return Optional.empty();
        }
        return afterResume(error, giveUp, recovery);
    }

    // Past its budget a step no one names waits for the person rather than recovering by itself once more.
    private static RunBoundaries.FailingStep failing(
            final AppError error,
            final PauseDecider.Recovery recovery,
            final int pausedBefore,
            @Nullable final GiveUp<?> giveUp) {
        final int budget = recovery.pausesBeforeFlagging();
        final boolean automatic = recovery.isAutomatic() && pausedBefore < budget;
        log.debug(
                "Pausing on a model-call error code={} recovery={} pausedBefore={} budget={} automatic={}",
                error.code(),
                recovery,
                pausedBefore,
                budget,
                automatic);
        return giveUp == null
                ? new RunBoundaries.FailingStep(null, 0, 0, automatic)
                : new RunBoundaries.FailingStep(giveUp.step().segmentId(), pausedBefore + 1, budget, automatic);
    }

    private <T> Optional<Step<T>> afterResume(
            final AppError error, final GiveUp<T> giveUp, final PauseDecider.Recovery recovery) {
        // The person's Retry now while the provider is down is not the step's fault; after a stalled or failing step
        // it still counts, so a step that keeps timing out is flagged however it was retried.
        final boolean outage =
                recovery == PauseDecider.Recovery.OUTAGE || recovery == PauseDecider.Recovery.UNLOADED_MODEL;
        final int paused = boundaries.takeRetryByPerson() && outage
                ? pauses.getOrDefault(countKey(giveUp, recovery), 0)
                : pauses.merge(countKey(giveUp, recovery), 1, Integer::sum);
        if (boundaries.takeSkipRequest()) {
            log.info("Skipping step={} as asked from the pause code={}; it is flagged", giveUp.step(), error.code());
            return Optional.of(new Step.Done<>(giveUp.flag().apply(error)));
        }
        log.debug("Resumed step={} pausesSoFar={}", giveUp.step(), paused);
        return Optional.empty();
    }

    private static String countKey(final GiveUp<?> giveUp, final PauseDecider.Recovery recovery) {
        return giveUp.step().key() + "/" + recovery;
    }

    private static <T> Result<T> withSegment(@Nullable final String segmentId, final Supplier<Result<T>> call) {
        return SegmentLogContext.within(segmentId, () -> guarded(segmentId, call));
    }

    // A step that throws is a fault of the run, not of the book: it is answered as internal and recovered from like
    // a provider error, so one bad segment cannot end a night's run. A virtual-machine error is never caught.
    private static <T> Result<T> guarded(@Nullable final String segmentId, final Supplier<Result<T>> call) {
        try {
            return Objects.requireNonNull(call.get(), "call result");
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (RuntimeException | Error thrown) {
            log.error(
                    "A run step threw instead of answering segmentId={}; it is retried as an internal error",
                    segmentId,
                    thrown);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Unexpected error in the run",
                    "An unexpected error interrupted this segment; the run will try it again.",
                    null,
                    thrown));
        }
    }

    // A model error the run cannot recover from is an application fault, never a provider error that Retry now
    // could fix, so it ends the run as internal. An internal error was already logged where it was built.
    private static AppError endingError(final AppError error) {
        if (error.code() == ErrorCode.internal) {
            return error;
        }
        final IllegalStateException cause = new IllegalStateException(
                "A model call answered " + error.code() + ", which a run cannot recover from");
        log.error("Translation job stopped by a model error it cannot recover from code={}", error.code(), cause);
        return AppError.of(
                ErrorCode.internal,
                "Translation job failed",
                "An unexpected failure stopped this translation job.",
                null,
                cause);
    }

    /**
     * A step the run may give up on: what kind of step it is and the segment it is for.
     *
     * @param kind the step's kind, such as {@code draft}, {@code review} or {@code decide}
     * @param segmentId the segment the step is for, the first of its chunk for a chunk's reviewer call
     */
    record StepName(String kind, String segmentId) {

        /** Rejects a missing part. */
        StepName {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(segmentId, "segmentId");
        }

        /** The step's key in the pause count, unique within the run. */
        String key() {
            return kind + ":" + segmentId;
        }

        @Override
        public String toString() {
            return key();
        }
    }

    /**
     * How a step the run may give up on is flagged.
     *
     * @param step the step's name in the pause count
     * @param flag turns the step's last error into its flagged answer
     */
    private record GiveUp<T>(StepName step, Function<AppError, T> flag) {}
}
