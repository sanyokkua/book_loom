package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.RecoveryWaiting;

/**
 * Where a run paused on a provider error stands in its automatic recovery, as the banner, the title bar and the
 * connection chip show it.
 *
 * @param status whether a wake is scheduled, or why none is
 * @param attempt the wake about to happen, or the last one made when none is scheduled
 * @param downSince when the outage began, in the person's own zone
 * @param nextTryAt when the next wake probes the provider, or {@code null} when none is scheduled
 * @param probeFailure the code the last probe answered, or {@code null} before the first probe
 * @param secondsLeft whole seconds until the next wake, never negative; zero when none is scheduled
 */
public record RecoveryState(
        RecoveryWaiting.Status status,
        int attempt,
        LocalTime downSince,
        @Nullable Instant nextTryAt,
        @Nullable ErrorCode probeFailure,
        long secondsLeft) {

    /** Rejects a missing part or a negative countdown. */
    public RecoveryState {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(downSince, "downSince");
        if (secondsLeft < 0) {
            throw new IllegalArgumentException("secondsLeft must not be negative");
        }
    }

    /**
     * The state an engine announcement describes, as of {@code now}.
     *
     * @param event the announcement
     * @param now the current instant
     * @param zone the person's zone, which the down-since time is shown in
     * @return the state; never null
     */
    public static RecoveryState of(final RecoveryWaiting event, final Instant now, final ZoneId zone) {
        Objects.requireNonNull(event, "event");
        final LocalTime since = LocalTime.ofInstant(event.downSince(), Objects.requireNonNull(zone, "zone"));
        return new RecoveryState(event.status(), event.attempt(), since, event.nextTryAt(), event.probeFailure(), 0)
                .at(now);
    }

    /**
     * The same state with its countdown read at {@code now}.
     *
     * @param now the current instant
     * @return a copy whose {@code secondsLeft} counts to the next wake from {@code now}
     */
    public RecoveryState at(final Instant now) {
        final Instant next = nextTryAt;
        final long left =
                next == null ? 0 : Math.max(0, Duration.between(now, next).toSeconds());
        return new RecoveryState(status, attempt, downSince, nextTryAt, probeFailure, left);
    }

    /**
     * Whether the run will try the provider again by itself.
     *
     * @return {@code true} while a wake is scheduled, {@code false} once the person or the time limit took over
     */
    public boolean isWaiting() {
        return status == RecoveryWaiting.Status.WAITING;
    }

    /**
     * Whether a possibly absent recovery will try the provider again by itself.
     *
     * @param state the recovery, or {@code null} when the run is not recovering
     * @return {@code true} while a wake is scheduled, {@code false} otherwise
     */
    public static boolean waits(final @Nullable RecoveryState state) {
        return state != null && state.isWaiting();
    }
}
