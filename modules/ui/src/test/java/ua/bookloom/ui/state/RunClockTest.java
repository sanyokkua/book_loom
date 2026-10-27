package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The running time the elapsed figure and the ledger's step times are taken from: paused spans are not in it. */
class RunClockTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");

    // IF a pause counted as running time, THEN a lunch break would lengthen the elapsed time and every step's time.
    @Test
    void active_tenMinutesThreePausedFiveMore_isFifteenMinutes() {
        final RunClock clock = new RunClock(T0);
        clock.paused(T0.plus(Duration.ofMinutes(10)));
        clock.resumed(T0.plus(Duration.ofMinutes(13)));

        assertThat(clock.active(T0.plus(Duration.ofMinutes(18)))).isEqualTo(Duration.ofMinutes(15));
    }

    // IF the clock kept running inside an open pause, THEN a run paused now would still age.
    @Test
    void active_whilePaused_standsStill() {
        final RunClock clock = new RunClock(T0);
        clock.paused(T0.plusSeconds(60));

        assertThat(clock.active(T0.plusSeconds(3600))).isEqualTo(Duration.ofSeconds(60));
    }

    // IF the clock kept running after the end, THEN a finished run's elapsed time would grow by itself.
    @Test
    void elapsed_afterTheEnd_stopsCounting() {
        final RunClock clock = new RunClock(T0);
        clock.ended(T0.plusSeconds(240));

        assertThat(clock.elapsed(T0.plusSeconds(900))).isEqualTo(Duration.ofMinutes(4));
    }
}
