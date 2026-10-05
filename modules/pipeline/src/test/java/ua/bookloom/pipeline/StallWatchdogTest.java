package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;

/** The watchdog ends a call only past one and a half times its own timeout, or after twenty idle minutes. */
class StallWatchdogTest {

    private final ScriptedClock clock = new ScriptedClock();
    private final JobControl control = new JobControl(Set.of());
    private final StallWatchdog watchdog = new StallWatchdog(control, clock);

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    // A judge call has a 90 s timeout, so the watchdog ends it at 135 s and not a second before.
    @ParameterizedTest
    @CsvSource({"134, false", "135, false", "136, true"})
    void check_judgeCallOutstanding_endsItOnlyPastOneHundredThirtyFiveSeconds(final long seconds, final boolean ends) {
        inJudgeCall();

        clock.advance(Duration.ofSeconds(seconds));
        watchdog.check();

        assertThat(Thread.interrupted()).isEqualTo(ends);
        assertThat(watchdog.takeStall() != null).isEqualTo(ends);
    }

    @Test
    void check_judgeCallEnded_endsNothing() {
        inJudgeCall();
        watchdog.onEvent(new ModelCallFinished(
                null, CallKind.REVIEW, Duration.ofSeconds(3), null, 0, false, List.of("s1"), 1, null));

        clock.advance(Duration.ofMinutes(5));
        watchdog.check();

        assertThat(Thread.interrupted()).isFalse();
    }

    @Test
    void check_noDecisionForTwentyMinutes_endsTheCallInFlight() {
        control.claimRun();
        watchdog.start((period, tick) -> () -> {});
        control.enterModelCall();
        watchdog.onEvent(new ModelCallStarted("s1", CallKind.DRAFT, List.of("s1"), 1, 2, Duration.ofMinutes(30), null));

        clock.advance(Duration.ofMinutes(21));
        watchdog.check();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(watchdog.takeStall())
                .isNotNull()
                .extracting(StallWatchdog.Stall::ceiling)
                .isEqualTo(Duration.ofMinutes(20));
    }

    // A paused run is waiting on purpose, however long it waits.
    @Test
    void check_runNotRunning_endsNothing() {
        clock.advance(Duration.ofHours(3));

        watchdog.check();

        assertThat(watchdog.takeStall()).isNull();
    }

    private void inJudgeCall() {
        control.claimRun();
        control.enterModelCall();
        watchdog.onEvent(
                new ModelCallStarted(null, CallKind.REVIEW, List.of("s1"), 1, 1, Duration.ofSeconds(90), null));
    }
}
