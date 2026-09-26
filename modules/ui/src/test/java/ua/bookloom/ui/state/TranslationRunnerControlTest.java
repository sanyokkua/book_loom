package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javafx.application.Platform;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.StageStarted;

/** Pause, resume and cancel: the requested state shows at once, the reached state only when the engine acts. */
class TranslationRunnerControlTest extends RunnerTestBase {

    // ---- pause and resume -----------------------------------------------------------------------------------------

    // IF pause() waited for the engine to act, THEN the control would appear dead until the next safe boundary.
    @Test
    void pause_whileRunning_publishesPausingBeforeTheJobIsAskedAndNotPausedYet() throws Exception {
        final AtomicReference<RunState> seenByTheJob = new AtomicReference<>();
        startJob();
        job.onControl(
                () -> Platform.runLater(() -> seenByTheJob.set(mirror.runState().get())));

        runner.pause();

        assertThat(state()).isEqualTo(RunState.PAUSING);
        assertThat(job.calls()).contains("pause");
        assertThat(seenByTheJob.get()).isEqualTo(RunState.PAUSING);
        assertThat(states).containsExactly(RunState.RUNNING, RunState.PAUSING);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF PAUSED were published on request, THEN the screen would claim a pause the engine has not reached.
    @Test
    void pause_thenThePausedEvent_publishesPausedAtOnceWithoutATick() throws Exception {
        startJob();
        runner.pause();
        WaitForAsyncUtils.waitForFxEvents();

        job.emit(new Paused(PauseReason.REQUESTED, null, progress(5, 0, 5)));
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF counts froze while a pause was pending, THEN the person would think the run had hung.
    @Test
    void pause_countsAdvanceWhilePausing_stateStaysPausing() throws Exception {
        startJob();
        runner.pause();
        WaitForAsyncUtils.waitForFxEvents();

        job.emit(decided("s-412", SegmentStatus.ACCEPTED, progress(412, 0, 88)));
        deliverAndTick();
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(412);
        assertThat(state()).isEqualTo(RunState.PAUSING);

        job.emit(decided("s-413", SegmentStatus.ACCEPTED, progress(413, 0, 87)));
        deliverAndTick();

        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(413);
        assertThat(state()).isEqualTo(RunState.PAUSING);
        assertThat(states).containsExactly(RunState.RUNNING, RunState.PAUSING);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF resume() published RUNNING itself, THEN the screen would claim a resume the engine has not made.
    @Test
    void resume_afterPaused_delegatesAndRunningOnlyOnTheResumedEvent() throws Exception {
        startJob();
        job.emit(new Paused(PauseReason.AFTER_SEGMENT, null, progress(5, 0, 5)));
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(state()).isEqualTo(RunState.PAUSED);

        runner.resume();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(job.calls()).contains("resume");
        assertThat(state()).isEqualTo(RunState.PAUSED);

        job.emit(new Resumed(progress(5, 0, 5)));
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.RUNNING, RunState.PAUSED, RunState.RUNNING);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF a pause requested and then withdrawn by resume() stayed at PAUSING, THEN the screen would show a pending pause
    // for the rest of the run, because the engine sends no event when it drops the request.
    @Test
    void resume_whilePausing_publishesRunningAndDelegates() throws Exception {
        startJob();
        runner.pause();
        WaitForAsyncUtils.waitForFxEvents();

        runner.resume();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(job.calls()).contains("resume");
        assertThat(states).containsExactly(RunState.RUNNING, RunState.PAUSING, RunState.RUNNING);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF pause() after the engine paused published PAUSING, THEN a paused screen would flip back to a pending pause.
    @Test
    void pause_afterThePausedEvent_publishesNothingAndDoesNotAskTheJob() throws Exception {
        startJob();
        job.emit(new Paused(PauseReason.AFTER_SEGMENT, null, progress(5, 0, 5)));
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();

        runner.pause();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.RUNNING, RunState.PAUSED);
        assertThat(job.calls()).doesNotContain("pause");
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF pause() after a stop published PAUSING, THEN it would overwrite STOPPING.
    @Test
    void pause_afterCancel_publishesNothingAndDoesNotAskTheJob() throws Exception {
        startJob();
        runner.cancel();
        WaitForAsyncUtils.waitForFxEvents();

        runner.pause();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.RUNNING, RunState.STOPPING);
        assertThat(job.calls()).doesNotContain("pause");
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF pause() were honoured in the export stage, THEN the screen would show a pending pause the engine ignores.
    @Test
    void pause_afterTheExportStageStarted_publishesNothingAndDoesNotAskTheJob() throws Exception {
        startJob();
        job.emit(new StageStarted(JobStage.EXPORT, progress(10, 0, 0)));
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();

        runner.pause();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.RUNNING);
        assertThat(job.calls()).doesNotContain("pause");
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF cancel() after the run returned were forwarded, THEN a finished run's job would be asked to stop.
    @Test
    void cancel_afterTheRunReturned_publishesNothingAndDoesNotAskTheJob() throws Exception {
        startJob();
        job.finish(Result.ok(completedReport(1)));
        awaitState(RunState.COMPLETED);
        WaitForAsyncUtils.waitForFxEvents();

        runner.cancel();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.RUNNING, RunState.COMPLETED);
        assertThat(job.calls()).doesNotContain("cancel");
    }

    // ---- cancel ---------------------------------------------------------------------------------------------------

    // IF cancel() waited for the run to return, THEN a person pressing Stop would see nothing happen for a whole call
    // to the model.
    @Test
    void cancel_whileRunning_reachesTheJobAndPublishesStoppingBeforeItIsAsked() throws Exception {
        final AtomicReference<RunState> seenByTheJob = new AtomicReference<>();
        startJob();
        job.onControl(
                () -> Platform.runLater(() -> seenByTheJob.set(mirror.runState().get())));

        runner.cancel();

        assertThat(state()).isEqualTo(RunState.STOPPING);
        assertThat(job.calls()).contains("cancel");
        assertThat(seenByTheJob.get()).isEqualTo(RunState.STOPPING);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }

    // IF STOPPED were published on request, THEN the screen would offer a new run while the old one is still writing.
    @Test
    void cancel_thenTheRunReturnsCancelled_stoppedOnlyAfterTheReturn() throws Exception {
        startJob();
        runner.cancel();
        WaitForAsyncUtils.waitForFxEvents();
        job.drain();
        assertThat(state()).isEqualTo(RunState.STOPPING);

        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        assertThat(states).containsExactly(RunState.RUNNING, RunState.STOPPING, RunState.STOPPED);
        assertThat(onFx(() -> mirror.failure().get())).isNull();
    }

    static Stream<Arguments> eventsAfterCancel() {
        final JobProgress at = progress(5, 0, 5);
        return Stream.of(
                Arguments.of(Named.<JobEvent>of("Paused", new Paused(PauseReason.REQUESTED, null, at))),
                Arguments.of(Named.<JobEvent>of("Resumed", new Resumed(at))));
    }

    // IF a Paused or Resumed event overwrote STOPPING, THEN a stop in flight would read as a pause or a run.
    @ParameterizedTest
    @MethodSource("eventsAfterCancel")
    void cancel_thenAPausedOrResumedEvent_stateStaysStoppingUntilTheRunReturns(final JobEvent event) throws Exception {
        startJob();
        runner.cancel();
        WaitForAsyncUtils.waitForFxEvents();

        job.emit(event);
        job.drain();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(state()).isEqualTo(RunState.STOPPING);
        assertThat(states).containsExactly(RunState.RUNNING, RunState.STOPPING);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
        assertThat(states).containsExactly(RunState.RUNNING, RunState.STOPPING, RunState.STOPPED);
    }
}
