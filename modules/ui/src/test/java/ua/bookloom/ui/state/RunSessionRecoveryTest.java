package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.pipeline.Resumed;

/**
 * A run that waits for the provider by itself is visible: where the recovery stands, a countdown that moves, one log
 * line per step, and a Pause that reaches the job so the person can take the run over.
 */
class RunSessionRecoveryTest extends LiveSessionTestBase {

    private static final AppError DOWN = AppError.of(ErrorCode.unreachable, "Model server unreachable", "Down.");
    private static final Duration UNTIL_NEXT = Duration.ofSeconds(252);

    // IF the waiting run published nothing, THEN in the morning it would look stuck instead of retrying.
    @Test
    void waitingAnnouncement_publishesWhereTheRecoveryStandsAndOneLogLine() {
        final RunSession session = pausedSession();

        session.onEvent(waiting(6, ErrorCode.unreachable));
        tick(session);

        assertThat(recovery())
                .isEqualTo(new RecoveryState(
                        RecoveryWaiting.Status.WAITING,
                        6,
                        LocalTime.MIDNIGHT,
                        clock.instant().plus(UNTIL_NEXT),
                        ErrorCode.unreachable,
                        252));
        assertThat(logEntries())
                .contains(new LogEntry(LogKind.WAITING, List.of("6", "00:04:12", "unreachable", "waiting")));
    }

    @Test
    void countdown_movesWithTheClockOnEachTick() {
        final RunSession session = pausedSession();
        session.onEvent(waiting(1, null));

        clock.advance(Duration.ofSeconds(12));
        tick(session);

        assertThat(recovery()).extracting(RecoveryState::secondsLeft).isEqualTo(240L);
    }

    @Test
    void resumed_clearsTheRecovery() {
        final RunSession session = pausedSession();
        session.onEvent(waiting(2, ErrorCode.unreachable));
        tick(session);

        session.onEvent(new Resumed(progress(1, 0, 2)));
        tick(session);

        assertThat(recovery()).isNull();
    }

    // IF a pause were ignored because the run is already paused, THEN the person could never stop the retries.
    @Test
    void requestPause_whileWaiting_isPassedToTheJob() {
        final RunSession session = pausedSession();
        session.onEvent(waiting(3, ErrorCode.upstream));

        assertThat(session.requestPause()).isTrue();
    }

    @Test
    void requestPause_afterThePersonHeldTheRun_isIgnored() {
        final RunSession session = pausedSession();
        session.onEvent(new RecoveryWaiting(
                RecoveryWaiting.Status.HELD, DOWN, 3, clock.instant(), null, null, progress(1, 0, 2)));

        assertThat(session.requestPause()).isFalse();
    }

    private RunSession pausedSession() {
        final RunSession session = session();
        session.onEvent(new Paused(PauseReason.ON_ERROR, DOWN, progress(1, 0, 2), "s-2", 1, 10));
        WaitForAsyncUtils.waitForFxEvents();
        return session;
    }

    private RecoveryWaiting waiting(final int attempt, final @Nullable ErrorCode probe) {
        final Instant now = clock.instant();
        return new RecoveryWaiting(
                RecoveryWaiting.Status.WAITING, DOWN, attempt, now, now.plus(UNTIL_NEXT), probe, progress(1, 0, 2));
    }

    private @Nullable RecoveryState recovery() {
        return onFx(() -> mirror.review().recovery().get());
    }
}
