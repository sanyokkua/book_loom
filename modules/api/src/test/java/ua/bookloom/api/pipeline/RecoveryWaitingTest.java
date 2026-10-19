package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/** A recovery announcement schedules a next try exactly while it waits, and the default probe finds the provider. */
class RecoveryWaitingTest {

    private static final AppError DOWN = AppError.of(ErrorCode.unreachable, "Down", "Nothing is listening.");
    private static final Instant SINCE = Instant.parse("2026-10-02T02:14:00Z");
    private static final JobProgress PROGRESS = new JobProgress(JobStage.TRANSLATE, 1, 1, 1, 0, 2);

    // IF a waiting announcement could lack its next try, THEN the banner's countdown would have nothing to count to.
    @ParameterizedTest
    @CsvSource(
            nullValues = "NONE",
            value = {"WAITING, NONE", "GAVE_UP, 2026-10-02T03:00:00Z", "HELD, 2026-10-02T03:00:00Z"})
    void constructor_scheduleDisagreesWithStatus_isRejected(
            final RecoveryWaiting.Status status, final @Nullable Instant nextTryAt) {
        assertThatThrownBy(() -> new RecoveryWaiting(status, DOWN, 1, SINCE, nextTryAt, null, PROGRESS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeAttempt_isRejected() {
        assertThatThrownBy(() ->
                        new RecoveryWaiting(RecoveryWaiting.Status.GAVE_UP, DOWN, -1, SINCE, null, null, PROGRESS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_waitingWithItsNextTry_keepsEveryPart() {
        final Instant next = SINCE.plusSeconds(15);

        final RecoveryWaiting waiting = new RecoveryWaiting(
                RecoveryWaiting.Status.WAITING, DOWN, 1, SINCE, next, ErrorCode.unreachable, PROGRESS);

        assertThat(waiting.nextTryAt()).isEqualTo(next);
        assertThat(waiting.probeFailure()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void assumeReachable_probe_answersAtOnce() {
        assertThat(ProviderProbe.ASSUME_REACHABLE.probe().data()).isEqualTo(Duration.ZERO);
    }
}
