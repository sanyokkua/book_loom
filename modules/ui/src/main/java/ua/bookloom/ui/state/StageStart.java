package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.Objects;
import ua.bookloom.api.llm.VerificationStage;

/**
 * One stage of a running provider test as it began, before it has sent any request.
 *
 * @param stage the stage that began
 * @param startedAt when the window heard of it, by the settings' clock; the card's live time counts from here
 */
public record StageStart(VerificationStage stage, Instant startedAt) {

    /** Rejects a missing part. */
    public StageStart {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(startedAt, "startedAt");
    }
}
