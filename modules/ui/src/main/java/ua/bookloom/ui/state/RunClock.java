package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.project.SegmentPath;

/**
 * How long a run has been running and how long it has left.
 *
 * <p>The elapsed time leaves out the spans the run was paused, and so does the time each decided segment took, so a
 * lunch break does not make the next estimate absurd. The time left is the plain average of the last
 * {@value #WINDOW} of those per-segment times, times the segments still pending: a window that long lets one slow
 * segment move the estimate by a twentieth of its excess rather than by a fifth, so it no longer swings from minutes
 * to hours between two glances. It stays unknown until enough segments of this run are decided for the average to mean
 * something (the first ones include loading the model). Not thread-safe: {@link RunSession} calls it under its publish
 * lock.
 */
@Slf4j
final class RunClock {

    /** Decided segments the run needs before an estimate is worth showing. */
    static final int MIN_DECIDED = 5;

    /** How many of the latest timed segments the average is taken over. */
    static final int WINDOW = 20;

    private static final double MILLIS_PER_SECOND = 1000.0;

    private final Instant started;
    private Duration pausedTotal = Duration.ZERO;
    private @Nullable Instant pausedSince;
    private @Nullable Instant ended;
    private Duration activeAtLastDecision = Duration.ZERO;
    private final Deque<Double> recentSeconds = new ArrayDeque<>();
    private double windowSeconds;
    private int decided;

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

    void decided(final Instant now) {
        final Duration active = active(now);
        final double seconds = active.minus(activeAtLastDecision).toMillis() / MILLIS_PER_SECOND;
        activeAtLastDecision = active;
        recentSeconds.addLast(seconds);
        windowSeconds += seconds;
        if (recentSeconds.size() > WINDOW) {
            windowSeconds -= recentSeconds.removeFirst();
        }
        decided++;
        if (decided == MIN_DECIDED) {
            log.debug("time left: {} segments decided, the estimate is now shown", decided);
        }
    }

    /**
     * Whether a decision counts toward the time-left average: an accepted or flagged segment does, one kept as it is
     * does not — it took no model call, so its near-zero time would drag the average down.
     */
    static boolean isTimed(final SegmentDecided decided) {
        final SegmentDetail detail = decided.detail();
        if (detail != null && detail.path() == SegmentPath.VERBATIM) {
            log.debug("time left: segment {} kept as it is, not timed", decided.segmentId());
            return false;
        }
        return decided.status() == SegmentStatus.ACCEPTED || decided.status() == SegmentStatus.FLAGGED;
    }

    Duration elapsed(final Instant now) {
        return active(now).truncatedTo(ChronoUnit.SECONDS);
    }

    @Nullable
    Duration timeLeft(final int pending) {
        if (decided < MIN_DECIDED) {
            return null;
        }
        final double averageSeconds = windowSeconds / recentSeconds.size();
        return Duration.ofMillis(Math.round(averageSeconds * pending * MILLIS_PER_SECOND))
                .truncatedTo(ChronoUnit.SECONDS);
    }

    private Duration active(final Instant now) {
        final Instant end = ended == null ? now : ended;
        final Instant since = pausedSince;
        final Duration inPause = since == null ? Duration.ZERO : Duration.between(since, end);
        return Duration.between(started, end).minus(pausedTotal).minus(inPause);
    }
}
