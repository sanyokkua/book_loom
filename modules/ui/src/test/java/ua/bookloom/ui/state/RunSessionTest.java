package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
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
        final RunSession session = session();
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
        final RunSession session = session();
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
        final RunSession session = session();
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.onEvent(new Paused(PauseReason.REQUESTED, null, progress(0, 0, 1)));
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF the pausing banner kept the wait, THEN it would read as waiting for the model, not as a pause being honoured.
    @Test
    void modelCallOutstanding_thenPauseRequested_clearsTheWaitingBannerAndStaysClear() {
        final RunSession session = session();
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.requestPause();
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF the stopping banner kept the wait, THEN Stop would look ignored.
    @Test
    void modelCallOutstanding_thenStopRequested_clearsTheWaitingBannerAndStaysClear() {
        final RunSession session = session();
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.requestStop();
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF the finished run left the wait shown, THEN a completed banner would still say the model is being waited for.
    @Test
    void modelCallOutstanding_thenRunFinished_clearsTheWaitingBanner() {
        final RunSession session = session();
        session.onEvent(new ModelCallStarted("s-1"));
        tickAfter(session, 12);

        session.finish(Result.ok(completedReport(1)), () -> {});
        tickAfter(session, 5);

        assertThat(waits).containsExactly(12, StateMirror.NOT_WAITING);
    }

    // IF a run with no request outstanding showed a wait, THEN export or the first segment would read as a slow model.
    @Test
    void tick_noModelCallStarted_publishesNoWaiting() {
        final RunSession session = session();

        tickAfter(session, 30);

        assertThat(waits).isEmpty();
    }

    private RunSession session() {
        return new RunSession(mirror, clock, runContext(), desk, executor);
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
        final RunSession session = session();
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

    // IF a translate-stage start blocked pauses, THEN the person could not pause a normal run.
    @Test
    void requestPause_afterTheTranslateStageStarted_isAccepted() {
        final RunSession session = session();
        session.onEvent(new StageStarted(JobStage.TRANSLATE, progress(0, 0, 3)));

        final boolean pause = session.requestPause();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(pause).isTrue();
        assertThat(states).containsExactly(RunState.PAUSING);
    }

    private AppError providerErrorOf(final ErrorCode code) {
        return AppError.of(code, "A title", "A message about " + code.name());
    }

    private @Nullable AppError publishedProviderError() {
        WaitForAsyncUtils.waitForFxEvents();
        return onFx(() -> mirror.review().providerError().get());
    }

    private @Nullable String publishedReviewSegment() {
        WaitForAsyncUtils.waitForFxEvents();
        return onFx(() -> mirror.review().reviewPauseSegment().get());
    }

    // IF a pause on an error were not shown as the provider-error state, THEN a hiccup would read as a crash; IF the
    // resume left the error, THEN a healthy run would still show it.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"unreachable", "validation"})
    void paused_onError_publishesTheErrorWhateverItsCodeAndTheNextResumeClearsIt(final ErrorCode code) {
        final RunSession session = session();
        final AppError error = providerErrorOf(code);

        session.onEvent(new Paused(PauseReason.ON_ERROR, error, progress(1, 0, 1)));

        assertThat(publishedProviderError()).isEqualTo(error);
        assertThat(states).containsExactly(RunState.PAUSED);

        session.onEvent(new Resumed(progress(1, 0, 1)));

        assertThat(publishedProviderError()).isNull();
        assertThat(states).containsExactly(RunState.PAUSED, RunState.RUNNING);
    }

    // IF a review pause did not name its segment, THEN the panel could not open the segment the run waits on.
    @ParameterizedTest
    @CsvSource({"ON_FLAGGED, ch05.xhtml:11", "AFTER_SEGMENT, ch01.xhtml:2"})
    void paused_forReview_publishesItsSegmentAndTheNextResumeClearsIt(final PauseReason reason, final String segment) {
        final RunSession session = session();

        session.onEvent(new Paused(reason, null, progress(1, 0, 1), segment));

        assertThat(publishedReviewSegment()).isEqualTo(segment);
        assertThat(publishedProviderError()).isNull();

        session.onEvent(new Resumed(progress(1, 0, 1)));

        assertThat(publishedReviewSegment()).isNull();
    }

    // IF an ordinary pause published a provider error, THEN a requested pause would show a failure banner.
    @Test
    void paused_requestedByThePerson_publishesNeitherAnErrorNorASegment() {
        final RunSession session = session();

        session.onEvent(new Paused(PauseReason.REQUESTED, null, progress(1, 0, 1)));

        assertThat(publishedProviderError()).isNull();
        assertThat(publishedReviewSegment()).isNull();
    }

    // IF a pause on an error arriving after a stop were shown, THEN a stopping run would read as a provider error.
    @Test
    void paused_onErrorAfterAStopRequest_publishesNoProviderError() {
        final RunSession session = session();
        session.requestStop();

        session.onEvent(new Paused(PauseReason.ON_ERROR, providerErrorOf(ErrorCode.timeout), progress(1, 0, 1)));

        assertThat(publishedProviderError()).isNull();
    }
}
