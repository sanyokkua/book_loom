package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How long a run has been running, leaving out the spans it was paused, so a lunch break neither lengthens the elapsed
 * time nor reaches the time each step is measured by ({@link ProgressLedger}). Not thread-safe: {@link RunSession}
 * calls it under its publish lock.
 */
final class RunClock {

    private final Instant started;
    private Duration pausedTotal = Duration.ZERO;
    private @Nullable Instant pausedSince;
    private @Nullable Instant ended;

    RunClock(final Instant started) {
        this.started = Objects.requireNonNull(started, "started");
    }

    void paused(final Instant now) {
        if (pausedSince == null && ended == null) {
            pausedSince = now;
        }
    }

    void resumed(final Instant now) {
        final Instant since = pausedSince;
        if (since != null) {
            pausedTotal = pausedTotal.plus(Duration.between(since, now));
            pausedSince = null;
        }
    }

    void ended(final Instant now) {
        resumed(now);
        if (ended == null) {
            ended = now;
        }
    }

    Duration elapsed(final Instant now) {
        return active(now).truncatedTo(ChronoUnit.SECONDS);
    }

    /** The running time to the millisecond, which the ledger measures steps by. */
    Duration active(final Instant now) {
        final Instant end = ended == null ? now : ended;
        final Instant since = pausedSince;
        final Duration inPause = since == null ? Duration.ZERO : Duration.between(since, end);
        return Duration.between(started, end).minus(pausedTotal).minus(inPause);
    }
}
