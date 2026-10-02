package ua.bookloom.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * When a run paused on a provider error tries the provider again by itself: soon at first, so a server restart costs
 * seconds, then every ten minutes, so a night-long outage costs a probe now and then; and not at all once the outage
 * has lasted {@link #MAX_OUTAGE}, after which the person decides.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecoverySchedule {

    /** How long an outage may last before the run stops waking by itself and waits for the person. */
    static final Duration MAX_OUTAGE = Duration.ofHours(12);

    /** The waits before the first wakes; every later wake waits as long as the last of these. */
    static final List<Duration> DELAYS = List.of(
            Duration.ofSeconds(15),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10));

    /**
     * The wait before a wake.
     *
     * @param wake the wake about to be waited for, counted from one
     * @param down how long the outage has lasted so far, never negative
     * @return the wait, or empty once the outage has lasted {@link #MAX_OUTAGE}
     */
    static Optional<Duration> delayBefore(final int wake, final Duration down) {
        Objects.requireNonNull(down, "down");
        if (wake < 1) {
            throw new IllegalArgumentException("wake counts from one: " + wake);
        }
        final Optional<Duration> delay = down.compareTo(MAX_OUTAGE) >= 0
                ? Optional.empty()
                : Optional.of(DELAYS.get(Math.min(wake, DELAYS.size()) - 1));
        log.debug("Recovery delay wake={} down={} delay={}", wake, down, delay.orElse(null));
        return delay;
    }
}
