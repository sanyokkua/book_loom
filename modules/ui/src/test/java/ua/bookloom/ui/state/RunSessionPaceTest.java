package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;

/** Tokens per second, the time left and the elapsed time a run reports. */
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

        session.onEvent(call("s-1", CallKind.JUDGE));
        tick(session);

        assertThat(throughput().tokensPerSecond()).isNull();
    }

    // IF an estimate showed before the model was warm, THEN the first slow segments would promise a wrong end.
    @Test
    void timeLeft_afterFourDecisions_isNotReported() {
        final RunSession session = session();

        decideEvery(session, 4, 12);
        tick(session);

        assertThat(throughput().timeLeft()).isNull();
    }

    // IF the average were not seconds per segment times the pending ones, THEN the estimate would be wrong.
    @Test
    void timeLeft_afterFiveDecisionsOfTwelveSeconds_isTwentyMinutes() {
        final RunSession session = session();

        decideEvery(session, 5, 12);
        tick(session);

        assertThat(throughput().timeLeft()).isEqualTo(Duration.ofMinutes(20));
    }

    // IF the segments before the last twenty still weighed in, THEN a run that sped up after warming would keep
    // promising its slow start.
    @Test
    void timeLeft_twentySlowThenTwentyFastSegments_followsOnlyTheLastTwenty() {
        final RunSession session = session();

        decideEvery(session, WINDOW, 60);
        decideEvery(session, WINDOW, 12);
        tick(session);

        assertThat(throughput().timeLeft()).isEqualTo(Duration.ofMinutes(20));
    }

    // IF one slow segment moved the estimate by a fifth of its excess, THEN the time left would leap from minutes to
    // hours on a single hard paragraph; over twenty segments it moves by a twentieth.
    @Test
    void timeLeft_oneSlowSegmentAfterNineteenSteadyOnes_movesByATwentiethOfItsExcess() {
        final RunSession session = session();

        decideEvery(session, WINDOW - 1, 12);
        decideEvery(session, 1, 252);
        tick(session);

        assertThat(throughput().timeLeft()).isEqualTo(Duration.ofMinutes(40));
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
