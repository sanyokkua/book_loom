package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.StageStarted;

/** What a run session decides on its own, deterministically, without a job thread. */
class RunSessionTest extends RunnerTestBase {

    private final MutableClock clock = new MutableClock();
    private List<Integer> waits;

    @BeforeEach
    void watchTheWaitingSeconds() {
        waits = new CopyOnWriteArrayList<>();
        onFx(() -> {
            mirror.waitingSeconds().addListener((o, before, after) -> waits.add(after.intValue()));
            return null;
        });
    }

    // IF the notice appeared early, or never updated, THEN the person would either be nagged about a normal request or
    // left with a frozen dashboard; IF the next decision did not clear it, THEN a finished wait would still be shown.
    @Test
    void modelCallOutstanding_pastThreshold_showsWaitingBanner() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new ModelCallStarted("s-1"));

        tickAfter(session, 9);
        tickAfter(session, 1);
        tickAfter(session, 0);
        tickAfter(session, 2);
        session.onEvent(decided("s-1", SegmentStatus.ACCEPTED, progress(1, 0, 1)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(waits).containsExactly(10, 12, StateMirror.NOT_WAITING);
    }

    // IF a request that ends and is followed by a repair kept the old start, THEN the notice would claim a wait
    // that the new request has not had.
    @Test
    void modelCallStarted_afterAShownWait_clearsItAndRestartsTheClock() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 11);

        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 9);
        tickAfter(session, 1);

        assertThat(waits).containsExactly(11, StateMirror.NOT_WAITING, 10);
    }

    // IF a wait were shown while paused, THEN the paused banner would say the model is being waited for.
    @Test
    void modelCallOutstanding_thenPaused_clearsTheWaitingBanner() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.onEvent(new Paused(PauseReason.REQUESTED, null, progress(0, 0, 1)));
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF the pausing banner kept the wait, THEN it would read as waiting for the model, not as a pause being honoured.
    @Test
    void modelCallOutstanding_thenPauseRequested_clearsTheWaitingBannerAndStaysClear() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.requestPause();
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF the stopping banner kept the wait, THEN Stop would look ignored.
    @Test
    void modelCallOutstanding_thenStopRequested_clearsTheWaitingBannerAndStaysClear() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.requestStop();
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF the finished run left the wait shown, THEN a completed banner would still say the model is being waited for.
    @Test
    void modelCallOutstanding_thenRunFinished_clearsTheWaitingBanner() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.finish(Result.ok(completedReport(1)), () -> {});
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF a run with no request outstanding showed a wait, THEN export or the first segment would read as a slow model.
    @Test
    void tick_noModelCallStarted_publishesNoWaiting() {
        final RunSession session = new RunSession(mirror, clock);

        tickAfter(session, 30);

        assertThat(waits).isEmpty();
    }

    private void tickAfter(final RunSession session, final long seconds) {
        clock.advance(Duration.ofSeconds(seconds));
        session.tick();
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a Stop pressed between the terminal publish and the runner becoming startable published STOPPING, THEN the
    // screen would read "stopping" after the run had completed.
    @Test
    void requests_afterFinish_areNoOpsAndTheTerminalStateStands() {
        final RunSession session = new RunSession(mirror, clock);
        session.finish(Result.ok(completedReport(1)), () -> {});

        final boolean stop = session.requestStop();
        final boolean pause = session.requestPause();
        final boolean resume = session.requestResume();
        session.onEvent(new Paused(PauseReason.REQUESTED, null, progress(1, 0, 0)));
        session.onEvent(new Resumed(progress(1, 0, 0)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stop).isFalse();
        assertThat(pause).isFalse();
        assertThat(resume).isFalse();
        assertThat(states).containsExactly(RunState.COMPLETED);
    }

    // IF a pause were honoured in export, THEN the screen would promise a pause the engine never reaches.
    @Test
    void requestPause_afterTheExportStageStarted_isRefused() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new StageStarted(JobStage.EXPORT, progress(3, 0, 0)));

        final boolean pause = session.requestPause();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(pause).isFalse();
        assertThat(states).isEmpty();
    }

    // IF a translate-stage start blocked pauses, THEN the person could not pause a normal run.
    @Test
    void requestPause_afterTheTranslateStageStarted_isAccepted() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new StageStarted(JobStage.TRANSLATE, progress(0, 0, 3)));

        final boolean pause = session.requestPause();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(pause).isTrue();
        assertThat(states).containsExactly(RunState.PAUSING);
    }
    // IF the engine ignored a pause that arrived once export began, THEN the screen would sit on "pausing" until the
    // book was written, promising a pause that never comes.
    @Test
    void requestPause_atExportStart_neverStaysPausing() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new StageStarted(JobStage.TRANSLATE, progress(0, 0, 1)));
        session.requestPause();

        session.onEvent(new StageStarted(JobStage.EXPORT, progress(1, 0, 0)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.PAUSING, RunState.RUNNING);
    }

    // IF a stop pending at export start were downgraded to running, THEN the person who pressed Stop would see the
    // button come back.
    @Test
    void requestStop_atExportStart_staysStopping() {
        final RunSession session = new RunSession(mirror, clock);
        session.onEvent(new StageStarted(JobStage.TRANSLATE, progress(0, 0, 1)));
        session.requestStop();

        session.onEvent(new StageStarted(JobStage.EXPORT, progress(1, 0, 0)));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(states).containsExactly(RunState.STOPPING);
    }
}
