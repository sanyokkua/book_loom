package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;

/**
 * One verification stage as the settings screen reports it, including a stage the verifier never reached.
 *
 * @param stage which finding this is
 * @param status its outcome; {@link StageStatus#SKIPPED} for a stage after a failed one, which the verifier omits
 * @param error the failure behind a failed stage, or the degraded discovery behind a soft pass; {@code null} when
 *     there is none to show
 * @param note a short qualifier such as "model list unavailable", or {@code null} when there is none
 * @param elapsed how long the stage took, or {@code null} when it was not measured
 * @param count how many models a listing held, or {@code null} when the stage counts nothing
 */
public record StageChip(
        VerificationStage stage,
        StageStatus status,
        @Nullable AppError error,
        @Nullable String note,
        @Nullable Duration elapsed,
        @Nullable Integer count) {

    /** Rejects a chip that names no stage or no status. */
    public StageChip {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(status, "status");
    }

    /**
     * A chip with no measured value.
     *
     * @param stage which finding this is
     * @param status its outcome
     * @param error the failure behind it, or {@code null}
     * @param note a short qualifier, or {@code null}
     */
    public StageChip(
            final VerificationStage stage,
            final StageStatus status,
            final @Nullable AppError error,
            final @Nullable String note) {
        this(stage, status, error, note, null, null);
    }
}
