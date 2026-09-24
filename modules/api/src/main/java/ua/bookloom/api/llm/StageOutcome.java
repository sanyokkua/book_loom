package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/** The result and optional explanation for one verification stage. */
public record StageOutcome(
        VerificationStage stage,
        StageStatus status,
        @Nullable AppError error,
        @Nullable String note) {

    /** Rejects a missing stage or status; error and note are optional. */
    public StageOutcome {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(status, "status");
    }
}
