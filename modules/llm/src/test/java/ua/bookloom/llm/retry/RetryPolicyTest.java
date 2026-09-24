package ua.bookloom.llm.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;

/** Pins retry eligibility, deterministic jitter, server advice, and interruption behavior. */
class RetryPolicyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void delayBeforeRetry_firstThreeAttemptsUseExponentialBase() {
        final RetryPolicy policy = policy(0.5, ignored -> {});

        assertThat(policy.delayBeforeRetry(1, null)).isEqualTo(Duration.ofMillis(500));
        assertThat(policy.delayBeforeRetry(2, null)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.delayBeforeRetry(3, null)).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void delayBeforeRetry_randomBoundsApplyTwentyFivePercentJitter() {
        assertThat(policy(0.0, ignored -> {}).delayBeforeRetry(1, null)).isEqualTo(Duration.ofMillis(375));
        assertThat(policy(1.0, ignored -> {}).delayBeforeRetry(1, null)).isEqualTo(Duration.ofMillis(625));
    }

    @Test
    void delayBeforeRetry_jitteredLocalDelayNeverExceedsEightSeconds() {
        assertThat(policy(1.0, ignored -> {}).delayBeforeRetry(12, null)).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void delayBeforeRetry_deltaSecondsTakePrecedenceOverLocalBackoff() {
        assertThat(policy(0.0, ignored -> {}).delayBeforeRetry(1, " 2 ")).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void delayBeforeRetry_httpDateUsesInjectedClock() {
        assertThat(policy(0.5, ignored -> {}).delayBeforeRetry(1, "Tue, 22 Sep 2026 12:00:05 GMT"))
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void delayBeforeRetry_invalidServerAdviceFallsBackToLocalBackoff() {
        assertThat(policy(0.5, ignored -> {}).delayBeforeRetry(2, "not-a-date")).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void delayBeforeRetry_expiredHttpDateMeansNoAdditionalWait() {
        assertThat(policy(0.5, ignored -> {}).delayBeforeRetry(1, "Tue, 22 Sep 2026 11:59:55 GMT"))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldRetry_retryableCodesStopAtThreeTotalAttempts() {
        final RetryPolicy policy = policy(0.5, ignored -> {});

        assertThat(policy.shouldRetry(ErrorCode.discoveryFailed, 1)).isTrue();
        assertThat(policy.shouldRetry(ErrorCode.upstream, 2)).isTrue();
        assertThat(policy.shouldRetry(ErrorCode.upstream, 3)).isFalse();
        assertThat(policy.shouldRetry(ErrorCode.auth, 1)).isFalse();
    }

    @Test
    void sleep_interruptedSleeperRestoresInterruptAndReportsCancellation() {
        final RetryPolicy policy = policy(0.5, ignored -> {
            throw new InterruptedException("test interruption");
        });

        assertThat(policy.sleep(Duration.ofMillis(500))).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static RetryPolicy policy(double randomValue, RetryPolicy.Sleeper sleeper) {
        return new RetryPolicy(CLOCK, () -> randomValue, sleeper);
    }
}
