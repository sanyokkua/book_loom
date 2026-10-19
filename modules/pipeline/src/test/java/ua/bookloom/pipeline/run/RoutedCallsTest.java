package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;

/**
 * The pause count of a step lives only while the step keeps failing: once it answers or is flagged it is forgotten, so
 * a night's run over thousands of segments holds no entry per segment.
 */
class RoutedCallsTest {

    private static final JobProgress PROGRESS = new JobProgress(JobStage.TRANSLATE, 1, 1, 0, 0, 3);

    private final RoutedCalls calls = new RoutedCalls(new ResumingBoundaries());

    @Test
    void untilAnsweredOrFlagged_stepThatAnswersAfterPauses_forgetsItsCount() {
        final Step<String> step = calls.untilAnsweredOrFlagged(
                () -> PROGRESS,
                "Book.txt:1",
                draft("Book.txt:1"),
                answers(ErrorCode.upstream, ErrorCode.timeout),
                error -> "X");

        assertThat(step).isEqualTo(new Step.Done<>("answer"));
        assertThat(calls.heldPauseCounts()).isZero();
    }

    @Test
    void untilAnsweredOrFlagged_stepFlaggedPastItsBudget_forgetsItsCount() {
        final Step<String> step = calls.untilAnsweredOrFlagged(
                () -> PROGRESS,
                "Book.txt:2",
                draft("Book.txt:2"),
                answers(ErrorCode.timeout, ErrorCode.timeout, ErrorCode.timeout),
                error -> "flagged " + error.code());

        assertThat(step).isEqualTo(new Step.Done<>("flagged timeout"));
        assertThat(calls.heldPauseCounts()).isZero();
    }

    private static RoutedCalls.StepName draft(final String segmentId) {
        return new RoutedCalls.StepName("draft", segmentId);
    }

    // Answers each error in turn, then the answer.
    private static Supplier<Result<String>> answers(final ErrorCode... errors) {
        final Deque<ErrorCode> left = new ArrayDeque<>(List.of(errors));
        return () -> left.isEmpty()
                ? Result.ok("answer")
                : Result.err(AppError.of(left.removeFirst(), "Scripted failure", "The scripted call failed."));
    }

    /** Resumes at once after every pause, as a recovery whose probe passes would. */
    private static final class ResumingBoundaries implements RunBoundaries {

        @Override
        public Optional<RunEnd> afterDecision(final Decision decision, final JobProgress progress) {
            return Optional.empty();
        }

        @Override
        public Optional<RunEnd> afterAbortedCall(final JobProgress progress) {
            return Optional.empty();
        }

        @Override
        public Optional<RunEnd> afterRoutedError(
                final AppError error, final JobProgress progress, final FailingStep step) {
            return Optional.empty();
        }

        @Override
        public boolean takeSkipRequest() {
            return false;
        }

        @Override
        public void callAnswered() {
            // Nothing to end: no outage is tracked here.
        }
    }
}
