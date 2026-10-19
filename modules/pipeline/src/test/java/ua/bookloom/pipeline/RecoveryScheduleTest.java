package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The wait before each wake of a recovering run, and the end of waking by itself. */
class RecoveryScheduleTest {

    @ParameterizedTest
    @CsvSource({"1, PT15S", "2, PT30S", "3, PT1M", "4, PT2M", "5, PT5M", "6, PT10M", "7, PT10M", "70, PT10M"})
    void delayBefore_wakeEarlyInAnOutage_followsTheSchedule(final int wake, final Duration expected) {
        assertThat(RecoverySchedule.delayBefore(wake, Duration.ofMinutes(1))).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"PT11H59M59S, true", "PT12H, false", "PT13H, false"})
    void delayBefore_outageNearTheLimit_wakesOnlyBeforeTwelveHours(final Duration down, final boolean wakes) {
        assertThat(RecoverySchedule.delayBefore(9, down).isPresent()).isEqualTo(wakes);
    }

    // The command line's --max-outage sets a shorter or longer limit than the window's twelve hours.
    @ParameterizedTest
    @CsvSource({"PT29M, PT30M, true", "PT30M, PT30M, false", "PT20H, PT24H, true", "PT24H, PT24H, false"})
    void delayBefore_outageAgainstItsOwnLimit_wakesOnlyBeforeIt(
            final Duration down, final Duration maxOutage, final boolean wakes) {
        assertThat(RecoverySchedule.delayBefore(9, down, maxOutage).isPresent()).isEqualTo(wakes);
    }
}
