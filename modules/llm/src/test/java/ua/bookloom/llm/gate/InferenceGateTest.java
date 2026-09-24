package ua.bookloom.llm.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** Verifies that failed and interrupted suppliers cannot strand the single-flight permit. */
class InferenceGateTest {

    @Test
    void run_supplierReturnsError_releasesPermitForNextCall() {
        final InferenceGate gate = new InferenceGate();
        final AppError expected = AppError.of(ErrorCode.upstream, "Unavailable", "Try again.");

        final Result<String> failed = gate.run(() -> Result.err(expected));
        final Result<String> next = gate.run(() -> Result.ok("served"));

        assertThat(failed.error()).isSameAs(expected);
        assertThat(next.data()).isEqualTo("served");
    }

    @Test
    void run_interruptedWaitReturnsCancelledAndRestoresInterruptFlag() {
        final InferenceGate gate = new InferenceGate();
        final Result<String> outer = gate.run(() -> {
            Thread.currentThread().interrupt();
            final Result<String> interrupted = gate.run(() -> Result.ok("must not run"));
            final boolean restored = Thread.currentThread().isInterrupted();
            Thread.interrupted();
            assertThat(Objects.requireNonNull(interrupted.error(), "error").code())
                    .isEqualTo(ErrorCode.cancelled);
            assertThat(restored).isTrue();
            return Result.ok("outer completed");
        });

        assertThat(outer.data()).isEqualTo("outer completed");
    }
}
