package ua.bookloom.pipeline.run;

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
 * the call again once the person resumes. A draft, the judge and a unit's summary are all routed here, so they pause
 * and fail alike.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class RoutedCalls {

    /** How many pauses one step may cause before the run flags it and goes on. */
    static final int PAUSES_BEFORE_FLAGGING = 2;

    private final RunBoundaries boundaries;
    // The pauses each give-up-able step caused so far in this run. Used from the job thread only.
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
     * Makes a call until it answers, the run ends, or the run gives up on it: after {@link #PAUSES_BEFORE_FLAGGING}
     * pauses for the same step, or when the person skips it from the pause, the step's error is turned into a flagged
     * answer by {@code flag} and the run goes on ({@code specs/translation-pipeline/spec.md} "Give up on a step that
     * keeps failing").
     *
     * @param work the run's work list, whose progress a pause reports
     * @param segmentId the segment the call is for, or {@code null} for a call of no single segment
     * @param step names the step for the pause count, unique within the run (its kind and its first segment's id)
     * @param call the call
     * @param flag turns the error the step last answered into its flagged answer
     * @return the answer, the flagged answer, or how the run ended while the call was routed
     */
    <T> Step<T> untilAnsweredOrFlagged(
            final WorkList work,
            @Nullable final String segmentId,
            final String step,
            final Supplier<Result<T>> call,
            final Function<AppError, T> flag) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(flag, "flag");
        return route(work::currentTranslationProgress, segmentId, call, new GiveUp<>(step, flag));
    }

    private <T> Step<T> route(
            final Supplier<JobProgress> progress,
            @Nullable final String segmentId,
            final Supplier<Result<T>> call,
            @Nullable final GiveUp<T> giveUp) {
        while (true) {
            final Result<T> result = withSegment(segmentId, call);
            if (result.isOk()) {
                return new Step.Done<>(Objects.requireNonNull(result.data(), "data"));
            }
            final AppError error = Objects.requireNonNull(result.error(), "error");
            final Optional<Step<T>> settled = afterError(error, progress.get(), giveUp);
            if (settled.isPresent()) {
                return settled.get();
            }
            log.debug("Making the call again segmentId={} after code={}", segmentId, error.code());
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
            final AppError error, final JobProgress progress, @Nullable final GiveUp<T> giveUp) {
        return switch (PauseDecider.route(error.code())) {
            case CANCELLED -> boundaries.afterAbortedCall(progress).<Step<T>>map(Step.Stopped::new);
            case PAUSE_OR_FAIL -> afterProviderError(error, progress, giveUp);
            case FLAG_AT_ONCE, FAIL -> Optional.of(new Step.Stopped<>(failedBy(endingError(error))));
        };
    }

    // The budget is checked before the pause, so the third failure of a step flags it instead of pausing again.
    private <T> Optional<Step<T>> afterProviderError(
            final AppError error, final JobProgress progress, @Nullable final GiveUp<T> giveUp) {
        if (giveUp != null && pauses.getOrDefault(giveUp.step(), 0) >= PAUSES_BEFORE_FLAGGING) {
            log.warn(
                    "Flagging step={} after {} pauses for it code={}; the run goes on",
                    giveUp.step(),
                    PAUSES_BEFORE_FLAGGING,
                    error.code());
            return Optional.of(new Step.Done<>(giveUp.flag().apply(error)));
        }
        final Optional<RunEnd> end = boundaries.afterRoutedError(error, progress);
        if (end.isPresent()) {
            return Optional.of(new Step.Stopped<>(end.get()));
        }
        return giveUp == null ? Optional.empty() : afterResume(error, giveUp);
    }

    private <T> Optional<Step<T>> afterResume(final AppError error, final GiveUp<T> giveUp) {
        final int paused = pauses.merge(giveUp.step(), 1, Integer::sum);
        if (boundaries.takeSkipRequest()) {
            log.info("Skipping step={} as asked from the pause code={}; it is flagged", giveUp.step(), error.code());
            return Optional.of(new Step.Done<>(giveUp.flag().apply(error)));
        }
        log.debug("Resumed step={} pausesSoFar={}", giveUp.step(), paused);
        return Optional.empty();
    }

    private static <T> Result<T> withSegment(@Nullable final String segmentId, final Supplier<Result<T>> call) {
        return SegmentLogContext.within(segmentId, () -> Objects.requireNonNull(call.get(), "call result"));
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
     * How a step the run may give up on is flagged.
     *
     * @param step the step's name in the pause count
     * @param flag turns the step's last error into its flagged answer
     */
    private record GiveUp<T>(String step, Function<AppError, T> flag) {}
}
