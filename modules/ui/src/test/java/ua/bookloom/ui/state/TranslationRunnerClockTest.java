package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;

/**
 * The activity log's times are the wall clock of the person's own zone, the one the file log is written in, not UTC.
 * The zone is set far from UTC (+14 hours), so a stamp taken in UTC could never pass for a local one.
 */
class TranslationRunnerClockTest extends RunnerTestBase {

    private static final ZoneId FAR_ZONE = ZoneId.of("Pacific/Kiritimati");
    private static final Duration SLACK = Duration.ofMinutes(2);

    private final TimeZone original = TimeZone.getDefault();

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(original);
    }

    // IF the window stamped its log lines in UTC, THEN a person in Kyiv would read times three hours away from the
    // file log's and from their own clock.
    @Test
    void logLine_productionRunner_isStampedInTheLocalZone() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(FAR_ZONE));
        runner = new TranslationRunner(mirror, executor, ticks, desk);
        startJob();

        job.emit(decided("s-1", SegmentStatus.ACCEPTED, progress(1, 0, 1)));
        deliverAndTick();
        final LocalTime stamped = Objects.requireNonNull(
                onFx(() -> mirror.activityLog().getFirst().time()), "time");

        assertThat(Duration.between(stamped, LocalTime.now(FAR_ZONE)).abs()).isLessThan(SLACK);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
    }
}
