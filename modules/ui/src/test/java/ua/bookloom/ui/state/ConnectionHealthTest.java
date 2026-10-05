package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;

/**
 * The failed calls the title bar counts: only calls that never succeeded. Each case scripts the attempts' ends in order
 * and reads the status a minute after the last one.
 */
class ConnectionHealthTest {

    private static final Instant START = Instant.parse("2026-10-02T00:10:00Z");
    private static final Duration ATTEMPT = Duration.ofSeconds(90);

    private static ModelCallFinished attempt(
            final CallKind kind, final String segment, final int attempt, final @Nullable ErrorCode failure) {
        return new ModelCallFinished(
                segment, kind, ATTEMPT, null, failure == null ? 30 : 0, false, List.of(segment), attempt, failure);
    }

    private static ConnectionStatus after(final ConnectionHealth health, final int minutes) {
        return health.snapshot(START.plus(Duration.ofMinutes(minutes)), null);
    }

    // IF a call that timed out once and was answered on its retry still counted, THEN the title bar would warn of
    // "1 failed call in 10 min" while every call has in fact succeeded.
    @Test
    void finished_failedThenAnsweredOnRetry_countsNoFailure() {
        final ConnectionHealth health = new ConnectionHealth();
        health.finished(attempt(CallKind.DIRECTED_FIX, "s-17", 1, ErrorCode.timeout), START);
        health.finished(attempt(CallKind.DIRECTED_FIX, "s-17", 2, null), START.plusSeconds(100));

        final ConnectionStatus status = after(health, 3);

        assertThat(status.failuresRecently()).isZero();
        assertThat(status.timeoutsRecently()).isZero();
    }

    // IF another call's answer cleared a failure, THEN a call that never succeeded would vanish from the warning.
    @Test
    void finished_failedThenAnotherCallAnswered_keepsTheFailure() {
        final ConnectionHealth health = new ConnectionHealth();
        health.finished(attempt(CallKind.DIRECTED_FIX, "s-17", 2, ErrorCode.timeout), START);
        health.finished(attempt(CallKind.DRAFT, "s-17", 1, null), START.plusSeconds(5));
        health.finished(attempt(CallKind.DIRECTED_FIX, "s-18", 1, null), START.plusSeconds(10));

        final ConnectionStatus status = after(health, 3);

        assertThat(status.failuresRecently()).isEqualTo(1);
        assertThat(status.timeoutsRecently()).isEqualTo(1);
    }

    // IF only the last failure were forgotten, THEN a call that failed twice before its answer would still count once.
    @Test
    void finished_twoFailuresThenTheAnswer_forgetsBoth() {
        final ConnectionHealth health = new ConnectionHealth();
        health.finished(attempt(CallKind.REVIEW, "s-2", 1, ErrorCode.unreachable), START);
        health.finished(attempt(CallKind.REVIEW, "s-2", 2, ErrorCode.timeout), START.plusSeconds(90));
        health.finished(attempt(CallKind.REVIEW, "s-2", 1, null), START.plusSeconds(200));

        assertThat(after(health, 5).failuresRecently()).isZero();
    }

    // A burst of failures between two snapshots must not grow the record past its bound.
    @Test
    void finished_moreFailuresThanTheBound_keepsTheNewest() {
        final ConnectionHealth health = new ConnectionHealth();

        java.util.stream.IntStream.range(0, ConnectionHealth.MAX_FAILURES + 100)
                .forEach(index -> health.finished(
                        attempt(CallKind.DRAFT, "s-" + index, 1, ErrorCode.upstream), START.plusSeconds(index)));

        assertThat(after(health, 6).failuresRecently()).isEqualTo(ConnectionHealth.MAX_FAILURES);
    }
}
