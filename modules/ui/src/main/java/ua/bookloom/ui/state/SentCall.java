package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;

/**
 * One model request of a running provider test, as the verifier announced it just before sending it.
 *
 * @param stage the stage that sent it
 * @param check the Advanced check it belongs to, or {@code null} for the inference probe
 * @param request the request with no reply yet, masked as the report is
 * @param sentAt when the window heard of it, by the settings' clock; the test card's live time counts from here
 */
public record SentCall(
        VerificationStage stage, @Nullable AdvancedCheck check, VerificationExchange request, Instant sentAt) {

    /** Rejects a missing part. */
    public SentCall {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(sentAt, "sentAt");
    }
}
