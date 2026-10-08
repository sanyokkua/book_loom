package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;

/** What a run's model calls show: the request it waits on, how the server answers, the pause and the live row's parts. */
class RunSessionCallsTest extends LiveSessionTestBase {

    private static final String LOCATOR = "ch9 · p02";
    private static final Duration JUDGE_TIMEOUT = Duration.ofSeconds(90);

    // IF the banner kept one clock across both attempts, THEN "attempt 2" would show the first attempt's wait.
    @Test
    void secondAttempt_restartsItsOwnClockAndKeepsTheCallsTotal() {
        final RunSession session = session();
        session.onEvent(started("s-2", LOCATOR, "Text."));
        session.onEvent(judgeAttempt(1, 2));
        clock.advance(JUDGE_TIMEOUT);
        session.onEvent(failed(1, ErrorCode.timeout));
        session.onEvent(judgeAttempt(2, 2));

        clock.advance(Duration.ofSeconds(12));
        tick(session);

        assertThat(waitingCall())
                .isEqualTo(new WaitingCall(
                        CallKind.REVIEW,
                        LOCATOR,
                        0,
                        2,
                        2,
                        Duration.ofSeconds(12),
                        JUDGE_TIMEOUT,
                        Duration.ofSeconds(102),
                        false));
    }

    // IF a long wait never offered a way out, THEN a hung call would hold the run until the timeout and its retry.
    @Test
    void attemptWaitingAMinute_isStuck() {
        final RunSession session = session();
        session.onEvent(started("s-2", LOCATOR, "Text."));
        session.onEvent(judgeAttempt(1, 2));

        clock.advance(Duration.ofSeconds(WaitNotice.STUCK_SECONDS));
        tick(session);

        assertThat(waitingCall()).extracting(WaitingCall::stuck).isEqualTo(true);
    }

    // IF an answered attempt left the notice up, THEN the banner would say the model is waited on while it works on.
    @Test
    void answeredAttempt_withdrawsTheWaitingCall() {
        final RunSession session = session();
        session.onEvent(judgeAttempt(1, 2));
        clock.advance(Duration.ofSeconds(15));
        tick(session);

        session.onEvent(new ModelCallFinished(
                "s-2", CallKind.REVIEW, Duration.ofSeconds(15), null, 30, false, List.of("s-2"), 1, null));
        tick(session);

        assertThat(waitingCall()).isNull();
    }

    // IF the chip did not count recent timeouts, THEN a server that keeps stalling would look as healthy as any other;
    // the judge of s-2 never succeeded, so both its failures count beside the answered judge of s-3.
    @Test
    void timeoutsAndAnswers_areCountedForTheConnectionChip() {
        final RunSession session = session();
        session.onEvent(failed(1, ErrorCode.timeout));
        session.onEvent(failed(2, ErrorCode.unreachable));
        session.onEvent(new ModelCallFinished(
                "s-3",
                CallKind.REVIEW,
                Duration.ofSeconds(6),
                new TokenUsage(500, 60, Duration.ofSeconds(2)),
                120,
                false,
                List.of("s-3"),
                1,
                null));
        clock.advance(Duration.ofSeconds(4));

        tick(session);

        assertThat(connection()).isEqualTo(new ConnectionStatus(Duration.ofSeconds(4), 1, 2, null, 30.0));
    }

    // IF the person's own pause read as a failed call, THEN the log would say "failed: cancelled" and the chip would
    // warn about an unsteady server that answered every request; the interrupted call is a neutral line, not counted.
    @Test
    void cancelledAttempt_isANeutralLineAndNoFailureForTheChip() {
        final RunSession session = session();

        session.onEvent(failed(1, ErrorCode.cancelled));
        tick(session);

        assertThat(shownLogEntries()).extracting(entry -> entry.kind()).containsExactly(LogKind.CALL_PAUSED);
        assertThat(connection().failuresRecently()).isZero();
    }

    // IF a failure ten minutes old still counted, THEN one bad moment would mark the server unsteady for the whole run.
    @Test
    void failureOlderThanTenMinutes_isForgotten() {
        final RunSession session = session();
        session.onEvent(failed(1, ErrorCode.timeout));

        clock.advance(ConnectionHealth.RECENT.plusSeconds(1));
        tick(session);

        assertThat(connection().failuresRecently()).isZero();
    }

    // IF the pause named neither the segment nor the call, THEN the banner could not say what Retry now repeats.
    @Test
    void pauseOnError_publishesTheSegmentTheFailedCallAndThePausesSpent() {
        final RunSession session = session();
        session.onEvent(started("s-2", LOCATOR, "Text."));
        session.onEvent(failed(2, ErrorCode.timeout));

        session.onEvent(new Paused(
                PauseReason.ON_ERROR,
                AppError.of(ErrorCode.timeout, "Timed out", "The model did not answer."),
                progress(1, 0, 2),
                "s-2",
                2,
                2));
        tick(session);

        final PauseNotice notice = onFx(() -> mirror.review().pauseNotice().get());
        assertThat(notice).isEqualTo(new PauseNotice(ErrorCode.timeout, LOCATOR, CallKind.REVIEW, 2, 2));
        assertThat(notice.isLastPause()).isTrue();
    }

    private static ModelCallStarted judgeAttempt(final int attempt, final int of) {
        return new ModelCallStarted("s-2", CallKind.REVIEW, List.of("s-2"), attempt, of, JUDGE_TIMEOUT, null);
    }

    private static ModelCallFinished failed(final int attempt, final ErrorCode code) {
        return new ModelCallFinished(
                "s-2", CallKind.REVIEW, JUDGE_TIMEOUT, null, 0, false, List.of("s-2"), attempt, code);
    }

    private WaitingCall waitingCall() {
        return onFx(() -> mirror.live().waitingCall().get());
    }

    private ConnectionStatus connection() {
        return onFx(() -> mirror.live().connection().get());
    }
}
