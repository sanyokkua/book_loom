package ua.bookloom.llm.retry;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;

/** Calculates bounded exponential backoff and honours valid HTTP {@code Retry-After} advice. */
@Slf4j
public final class RetryPolicy {

    /** Maximum number of provider requests for one logical chat. */
    public static final int MAX_ATTEMPTS = 3;

    private static final long BASE_DELAY_MILLIS = 500;
    private static final long MAX_BACKOFF_MILLIS = 8_000;
    private static final double MIN_JITTER_FACTOR = 0.75;
    private static final double JITTER_RANGE = 0.5;

    private final Clock clock;
    private final DoubleSupplier random;
    private final Sleeper sleeper;

    /** Injects time, randomness, and sleeping so policy behavior stays deterministic in tests. */
    public RetryPolicy(Clock clock, DoubleSupplier random, Sleeper sleeper) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** Whether this typed error may be retried within the three-attempt total budget. */
    public boolean shouldRetry(ErrorCode code, int failedAttempt) {
        Objects.requireNonNull(code, "code");
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("failedAttempt must be positive");
        }
        final boolean retry = code.isRetryable() && failedAttempt < MAX_ATTEMPTS;
        log.debug(
                "Retry decision code={} failedAttempt={} maxAttempts={} retry={}",
                code,
                failedAttempt,
                MAX_ATTEMPTS,
                retry);
        return retry;
    }

    /** Returns a valid server delay, or jittered local exponential backoff when advice is absent or invalid. */
    public Duration delayBeforeRetry(int failedAttempt, @Nullable String retryAfter) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("failedAttempt must be positive");
        }
        final Duration serverDelay = retryAfterDelay(retryAfter);
        final Duration delay = serverDelay == null ? localBackoff(failedAttempt) : serverDelay;
        log.debug(
                "Retry delay selected failedAttempt={} source={} delay={}",
                failedAttempt,
                serverDelay == null ? "local-backoff" : "retry-after",
                delay);
        return delay;
    }

    /** Sleeps outside the inference gate; an interruption is restored and reported as an unsuccessful wait. */
    public boolean sleep(Duration delay) {
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        if (delay.isZero()) {
            log.debug("Retry wait skipped delay={}", delay);
            return true;
        }
        try {
            sleeper.sleep(delay);
            log.debug("Retry wait completed delay={}", delay);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.debug("Retry wait interrupted; cancellation will be returned");
            return false;
        }
    }

    private Duration localBackoff(int failedAttempt) {
        long baseMillis = BASE_DELAY_MILLIS;
        for (int attempt = 1; attempt < failedAttempt && baseMillis < MAX_BACKOFF_MILLIS; attempt++) {
            baseMillis = Math.min(MAX_BACKOFF_MILLIS, baseMillis * 2);
        }
        final double randomValue = random.getAsDouble();
        if (!Double.isFinite(randomValue) || randomValue < 0.0 || randomValue > 1.0) {
            throw new IllegalStateException("random source must return a value in [0, 1]");
        }
        final double jitterFactor = MIN_JITTER_FACTOR + randomValue * JITTER_RANGE;
        final long jitteredMillis = Math.round(baseMillis * jitterFactor);
        return Duration.ofMillis(Math.min(jitteredMillis, MAX_BACKOFF_MILLIS));
    }

    private @Nullable Duration retryAfterDelay(@Nullable String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        final String value = retryAfter.trim();
        try {
            final long seconds = Long.parseLong(value);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // Retry-After is also permitted to contain an HTTP date.
        }
        try {
            final Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            final Duration untilRetry = Duration.between(clock.instant(), retryAt);
            return untilRetry.isNegative() ? Duration.ZERO : untilRetry;
        } catch (DateTimeException | ArithmeticException ignored) {
            log.debug("Retry-After value was invalid; using local backoff");
            return null;
        }
    }

    /** Injectable blocking wait; interruption is handled by {@link RetryPolicy#sleep(Duration)}. */
    @FunctionalInterface
    public interface Sleeper {

        /** Waits for the supplied duration. */
        void sleep(Duration delay) throws InterruptedException;
    }
}
