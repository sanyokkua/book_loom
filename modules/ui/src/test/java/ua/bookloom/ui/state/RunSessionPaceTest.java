package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;

/** Tokens per second, the time left from the step events and the elapsed time a run reports. */
class RunSessionPaceTest extends LiveSessionTestBase {

    private static final Duration THREE_SECONDS = Duration.ofSeconds(3);
    private static final Duration FIVE_SECONDS = Duration.ofSeconds(5);
    private static final int WINDOW = 20;

    // IF the rate were not completion tokens over generation time of the drafts, THEN a slow model would look fast.
    @Test
    void draftCalls_twentyOf90TokensIn3Seconds_reportThirtyTokensPerSecond() {
        final RunSession session = session();

        IntStream.range(0, WINDOW).forEach(n -> session.onEvent(draftCall(90, THREE_SECONDS, false)));
        tick(session);

        assertThat(throughput().tokensPerSecond()).isEqualTo(30.0);
        assertThat(throughput().estimated()).isFalse();
    }

    // IF an estimated usage were not marked, THEN a guess would be shown as the server's own figure.
    @Test
    void draftCalls_estimatedUsage_reportTheRateMarkedEstimated() {
        final RunSession session = session();

        IntStream.range(0, WINDOW).forEach(n -> session.onEvent(draftCall(115, FIVE_SECONDS, true)));
        tick(session);

        assertThat(throughput().tokensPerSecond()).isEqualTo(23.0);
        assertThat(throughput().estimated()).isTrue();
    }

    // IF only the newest 20 drafts did not count, THEN a rate from the start of the book would mask a slowdown.
    @Test
    void draftCalls_moreThanTheWindow_dropTheOldest() {
        final RunSession session = session();
        IntStream.range(0, WINDOW).forEach(n -> session.onEvent(draftCall(10, THREE_SECONDS, false)));

        IntStream.range(0, WINDOW).forEach(n -> session.onEvent(draftCall(90, THREE_SECONDS, false)));
        tick(session);

        assertThat(throughput().tokensPerSecond()).isEqualTo(30.0);
    }

    // IF a judge call counted, THEN its short answer would make a slow model look fast.
    @Test
    void judgeCall_isNotCounted() {
        final RunSession session = session();

        session.onEvent(call("s-1", CallKind.REVIEW));
        tick(session);

        assertThat(throughput().tokensPerSecond()).isNull();
    }

    /** The run's first snapshot, which tells the ledger how many segments the book has. */
    private void begin(final RunSession session, final int segments) {
        session.onEvent(new StageStarted(JobStage.TRANSLATE, progress(0, 0, segments)));
        session.onEvent(new StepsPlanned(StepKind.DRAFT, segments, false));
    }

    /** Ends {@code count} draft steps, each {@code seconds} after the last. */
    private void endDrafts(final RunSession session, final int count, final long seconds) {
        IntStream.range(0, count).forEach(n -> {
            clock.advance(Duration.ofSeconds(seconds));
            session.onEvent(new StepChanged(StepKind.DRAFT, "draft", n, StepState.ENDED, null, StepResult.DONE));
        });
    }

    // IF an estimate showed before enough steps were timed, THEN the first slow ones would promise a wrong end.
    @Test
    void timeLeft_afterFourDraftSteps_isNotReported() {
        final RunSession session = session();
        begin(session, 276);

        endDrafts(session, 4, 12);
        tick(session);

        assertThat(throughput().timeLeft()).isNull();
    }

    // IF the time left were not steps left times the measured seconds, THEN the estimate would be wrong.
    @Test
    void timeLeft_fiveDraftStepsOfTwelveSecondsWith100Left_isAbout20Minutes() {
        final RunSession session = session();
        begin(session, 105);

        endDrafts(session, 5, 12);
        tick(session);

        assertThat(throughput().timeLeft()).isEqualTo(Duration.ofMinutes(20));
    }

    // IF a slow stretch weighed more than the whole run, THEN the old window's problem would be back: a recent slow
    // spell projected over the book.
    @Test
    void timeLeft_aSlowStretchAfterSteadySteps_isAveragedOverTheWholeRun() {
        final RunSession session = session();
        begin(session, 120);
        endDrafts(session, 20, 12);
        endDrafts(session, 20, 60);
        tick(session);

        // 80 left at (20 x 12 s + 20 x 60 s) / 40 = 36 s: 2,880 s = 48 min.
        assertThat(throughput().timeLeft()).isEqualTo(Duration.ofMinutes(48));
    }

    // IF a chunk's own plan were the bar's total, THEN the bar would be full at the end of every chunk of the book.
    @Test
    void progress_oneChunkOfABookDone_isNotTheWholeBar() {
        final RunSession session = session();
        session.onEvent(new StageStarted(JobStage.TRANSLATE, progress(0, 0, 100)));
        session.onEvent(new StepsPlanned(StepKind.DRAFT, 5, false));
        endDrafts(session, 5, 12);
        session.onEvent(new SegmentDecided("s-5", SegmentStatus.ACCEPTED, null, progress(5, 0, 95), null));

        tick(session);

        assertThat(onFx(() -> mirror.live().progress().get().percent())).isEqualTo(5);
        assertThat(onFx(() -> mirror.live().progress().get().total())).isEqualTo(100);
    }

    // IF a pause counted as translation time, THEN a lunch break would be shown as a very slow run.
    @Test
    void elapsed_tenMinutesThreePausedFiveMore_isFifteenMinutes() {
        final RunSession session = session();
        clock.advance(Duration.ofMinutes(10));
        session.onEvent(new Paused(PauseReason.REQUESTED, null, progress(1, 0, 1)));
        clock.advance(Duration.ofMinutes(3));
        session.onEvent(new Resumed(progress(1, 0, 1)));
        clock.advance(Duration.ofMinutes(5));

        tick(session);

        assertThat(throughput().elapsed()).isEqualTo(Duration.ofMinutes(15));
    }

    // IF the elapsed time kept counting after the run ended, THEN the finished card would grow by itself.
    @Test
    void elapsed_afterTheRunEnded_stopsCounting() {
        final RunSession session = session();
        clock.advance(Duration.ofMinutes(4));
        session.finish(Result.ok(completedReport(1)), () -> {});

        clock.advance(Duration.ofMinutes(9));
        tick(session);

        assertThat(throughput().elapsed()).isEqualTo(Duration.ofMinutes(4));
    }
}
