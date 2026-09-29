package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
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

    private final RunBoundaries boundaries;

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
        while (true) {
            final Result<T> result = withSegment(segmentId, call);
            if (result.isOk()) {
                return new Step.Done<>(Objects.requireNonNull(result.data(), "data"));
            }
            final AppError error = Objects.requireNonNull(result.error(), "error");
            final Optional<RunEnd> end = afterError(error, progress.get());
            if (end.isPresent()) {
                return new Step.Stopped<>(end.get());
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

    private Optional<RunEnd> afterError(final AppError error, final JobProgress progress) {
        return switch (PauseDecider.route(error.code())) {
            case CANCELLED -> boundaries.afterAbortedCall(progress);
            case PAUSE_OR_FAIL -> boundaries.afterRoutedError(error, progress);
            case FLAG_AT_ONCE, FAIL -> Optional.of(failedBy(endingError(error)));
        };
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
}
