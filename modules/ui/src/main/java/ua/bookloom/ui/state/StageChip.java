package ua.bookloom.ui.state;

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
 */
public record StageChip(
        VerificationStage stage,
        StageStatus status,
        @Nullable AppError error,
        @Nullable String note) {

    /** Rejects a chip that names no stage or no status. */
    public StageChip {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(status, "status");
    }
}
