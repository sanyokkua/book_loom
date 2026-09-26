package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** A run that cannot be started, or whose teardown fails, still ends in a terminal state and frees the runner. */
class TranslationRunnerRobustnessTest extends RunnerTestBase {

    private FailingOnceExecutor flaky;
    private TranslationRunner flakyRunner;

    @BeforeEach
    void setUpFlakyExecutor() {
        flaky = new FailingOnceExecutor(executor);
        flakyRunner = new TranslationRunner(mirror, flaky, ticks);
    }

    private void assertARunCanStartAgain(final TranslationRunner target) throws Exception {
        final RecordingJob next = new RecordingJob();
        assertThat(target.start(next, REQUEST, SELECTION)).isTrue();
        next.awaitRunStarted();
        next.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    private @Nullable AppError failure() {
        return onFx(() -> mirror.failure().get());
    }

    // IF a job that cannot take a listener left the runner busy, THEN every later start would be refused for good.
    @Test
    void start_subscribeThrows_endsFailedWithAnInternalErrorAndTheRunnerIsFree() throws Exception {
        job.failOnSubscribe(new IllegalStateException("no listeners"));

        final boolean started = runner.start(job, REQUEST, SELECTION);
        awaitState(RunState.FAILED);

        assertThat(started).isFalse();
        assertThat(states).containsExactly(RunState.RUNNING, RunState.FAILED);
        assertThat(failure()).isNotNull().extracting(AppError::code).isEqualTo(ErrorCode.internal);
        assertThat(job.calls()).doesNotContain("run");
        assertThat(ticks.starts()).isZero();
        assertARunCanStartAgain(runner);
    }

    // IF a rejected submission left the cadence running or the listener attached, THEN a dead run would keep a timer
    // thread and a subscription alive.
    @Test
    void start_executorRejects_endsFailedStopsTheCadenceUnsubscribesAndTheRunnerIsFree() throws Exception {
        flaky.failNextWith(() -> {
            throw new RejectedExecutionException("the pool is shut down");
        });

        final boolean started = flakyRunner.start(job, REQUEST, SELECTION);
        awaitState(RunState.FAILED);

        assertThat(started).isFalse();
        assertThat(failure()).isNotNull().extracting(AppError::code).isEqualTo(ErrorCode.internal);
        assertThat(ticks.starts()).isEqualTo(1);
        assertThat(ticks.stops()).isEqualTo(1);
        assertThat(job.calls()).containsExactly("subscribe", "unsubscribe");
        assertARunCanStartAgain(flakyRunner);
    }

    // IF an Error were swallowed, THEN a broken JVM would look like a refused run; it must still clean up, then rise.
    @Test
    void start_executorThrowsAnError_cleansUpPublishesFailedThenRethrows() throws Exception {
        flaky.failNextWith(() -> {
            throw new InternalError("the pool broke");
        });

        assertThatThrownBy(() -> flakyRunner.start(job, REQUEST, SELECTION)).isInstanceOf(InternalError.class);
        awaitState(RunState.FAILED);

        assertThat(ticks.stops()).isEqualTo(1);
        assertThat(job.calls()).containsExactly("subscribe", "unsubscribe");
        assertARunCanStartAgain(flakyRunner);
    }

    // IF a cadence that cannot stop aborted the terminal path, THEN the outcome would never be published and the run
    // would read as running forever.
    @Test
    void terminalPath_stopperThrows_stillPublishesTheOutcomeAndUnsubscribes() throws Exception {
        ticks.failOnStop();
        startJob();
        emitAccepted(job, 1, 2);

        job.finish(Result.ok(completedReport(2)));
        awaitState(RunState.COMPLETED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.COMPLETED);
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(2);
        assertThat(ticks.stops()).isEqualTo(1);
        assertThat(job.calls()).contains("unsubscribe");
        assertARunCanStartAgain(runner);
    }
}
