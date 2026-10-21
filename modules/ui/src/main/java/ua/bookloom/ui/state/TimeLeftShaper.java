package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Turns the ledger's raw estimate into the figure a person reads: it follows the estimate by at most a tenth of its
 * value a minute, so one long stretch of added work does not make it leap, and it is rounded to the minute below an
 * hour and to ten minutes from an hour. The clock that passes is the run's own, without its pauses.
 *
 * <p>Time passing is allowed to bring the figure down: a countdown near the end falls by a larger share of its value
 * each minute than a tenth, and holding it to a tenth would leave it reading too long. Not thread-safe.
 */
final class TimeLeftShaper {

    private static final double MAX_SHARE_PER_MINUTE = 0.10;
    private static final double MILLIS_PER_MINUTE = 60_000.0;
    private static final long MINUTES_PER_HOUR = 60;
    private static final long ROUND_TO_MINUTES = 10;
    private static final long NEXT_HOUR_AT = 50;
    private static final double TEN = 10.0;

    private @Nullable Duration shown;
    private Duration at = Duration.ZERO;

    /**
     * Moves the shown figure toward an estimate.
     *
     * @param estimate what the ledger now expects to be left
     * @param active the run's clock without pauses, read now
     * @return the figure to show, rounded
     */
    Duration shape(final Duration estimate, final Duration active) {
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(active, "active");
        final Duration last = shown;
        if (last == null) {
            shown = estimate;
        } else {
            final Duration passed = active.minus(at);
            final long reach =
                    Math.round(last.toMillis() * MAX_SHARE_PER_MINUTE * passed.toMillis() / MILLIS_PER_MINUTE);
            final Duration high = last.plusMillis(reach);
            final Duration low = last.minusMillis(reach).minus(passed).compareTo(Duration.ZERO) < 0
                    ? Duration.ZERO
                    : last.minusMillis(reach).minus(passed);
            shown = estimate.compareTo(high) > 0 ? high : estimate.compareTo(low) < 0 ? low : estimate;
        }
        at = active;
        return round(shown);
    }

    /**
     * Rounds a length of time for reading.
     *
     * @param length a non-negative length of time
     * @return at least one minute; whole minutes below an hour; from an hour the nearest ten minutes, where 50 minutes
     *     past an hour reads as the next hour
     */
    static Duration round(final Duration length) {
        final long minutes = Math.max(1, Math.round(length.toMillis() / MILLIS_PER_MINUTE));
        if (minutes < MINUTES_PER_HOUR) {
            return Duration.ofMinutes(minutes);
        }
        long rounded = Math.round(minutes / TEN) * ROUND_TO_MINUTES;
        if (rounded % MINUTES_PER_HOUR == NEXT_HOUR_AT) {
            rounded += ROUND_TO_MINUTES;
        }
        return Duration.ofMinutes(rounded);
    }
}
