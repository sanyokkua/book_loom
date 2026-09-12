package ua.bookloom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code AppError}'s invariants and the retryability derivation.
 *
 * <p>Shape tests: as with {@link ResultTest}, the error type's shape is architecture (DD-14,
 * {@code 09_ERROR_HANDLING.md#app-error}) rather than a functional requirement.
 */
class AppErrorTest {

    /**
     * The five codes the specification annotates as retryable. Written out rather than derived from
     * {@code ErrorCode.isRetryable()} on purpose — deriving the expectation from the implementation would make this
     * test agree with any answer the enum gave.
     */
    private static final Set<ErrorCode> RETRYABLE = EnumSet.of(
            ErrorCode.timeout,
            ErrorCode.rateLimited,
            ErrorCode.unreachable,
            ErrorCode.discoveryFailed,
            ErrorCode.upstream);

    @Test
    void values_fifteenConstants_matchTheFrozenEnum() {
        assertThat(ErrorCode.values())
                .as("seam F2 is a fixed target; a constant added or removed here breaks every change compiled "
                        + "against it")
                .hasSize(15);
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void isRetryable_everyCode_matchesTheSpecifiedClassification(final ErrorCode code) {
        assertThat(code.isRetryable()).isEqualTo(RETRYABLE.contains(code));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void of_anyCode_derivesRetryableFromTheCode(final ErrorCode code) {
        final AppError error = AppError.of(code, "Title", "Message.");

        assertThat(error.retryable()).isEqualTo(code.isRetryable());
    }

    // Retryability is a property of the code, not a per-call-site judgement. Rejecting the mismatch at construction
    // is what stops two layers disagreeing about whether the same failure is worth retrying.
    @Test
    void constructor_retryableContradictingTheCode_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new AppError(ErrorCode.timeout, "Timed out", "The request timed out.", null, false, null))
                .withMessageContaining("derived from the code");
    }

    @Test
    void of_withDetailsAndCause_keepsBothAndStillDerivesRetryable() {
        final Throwable cause = new IllegalStateException("internal");
        final String details = SafeDetails.empty().withHttpStatus(503).render();

        final AppError error = AppError.of(ErrorCode.upstream, "Server error", "The provider failed.", details, cause);

        assertThat(error.details()).isEqualTo("httpStatus=503");
        assertThat(error.cause()).isSameAs(cause);
        assertThat(error.retryable()).isTrue();
    }
}
