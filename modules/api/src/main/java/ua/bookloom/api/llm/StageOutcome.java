package ua.bookloom.api.llm;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/**
 * The result and optional explanation for one verification stage.
 *
 * @param stage the verification stage this outcome describes
 * @param status the stage's pass/fail/skip classification
 * @param error the failure detail, or null when the stage did not fail
 * @param note a human-readable detail such as a discovered capability, or null when there is none
 * @param elapsed how long the stage took to run, or null when not measured
 * @param count a stage-specific measured count (e.g. models discovered), or null when not measured
 */
public record StageOutcome(
        VerificationStage stage,
        StageStatus status,
        @Nullable AppError error,
        @Nullable String note,
        @Nullable Duration elapsed,
        @Nullable Integer count) {

    /**
     * Preserves the original four-argument construction form, with no measured elapsed time or count.
     *
     * @param stage the verification stage this outcome describes
     * @param status the stage's pass/fail/skip classification
     * @param error the failure detail, or null when the stage did not fail
     * @param note a human-readable detail such as a discovered capability, or null when there is none
     */
    public StageOutcome(VerificationStage stage, StageStatus status, @Nullable AppError error, @Nullable String note) {
        this(stage, status, error, note, null, null);
    }

    /** Rejects a missing stage or status; every other field is optional. */
    public StageOutcome {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(status, "status");
    }
}
