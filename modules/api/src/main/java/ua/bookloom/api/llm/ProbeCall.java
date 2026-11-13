package ua.bookloom.api.llm;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One call of the Advanced features checks, as the Settings test card lists it under its check.
 *
 * <p>The bodies hold the checks' invented prompt and the model's reply, are shown to the person and logged at TRACE
 * only, and never hold a header or a credential.
 *
 * @param check the check the call belongs to
 * @param requestBody the request body as sent on the wire, for Copy as curl; empty when the client cannot show one
 * @param shownRequestBody the same body laid out for reading (indented when it is JSON)
 * @param shownResponseBody the reply's text laid out for reading, or an empty string when none arrived
 * @param httpStatus the response status, or zero when no response arrived
 * @param elapsed how long the call took, retries included
 * @param tokensPerSecond the generation speed the reply reported, or null when it did not say
 */
public record ProbeCall(
        AdvancedCheck check,
        String requestBody,
        String shownRequestBody,
        String shownResponseBody,
        int httpStatus,
        Duration elapsed,
        @Nullable Double tokensPerSecond) {

    /** Rejects a missing part. */
    public ProbeCall {
        Objects.requireNonNull(check, "check");
        Objects.requireNonNull(requestBody, "requestBody");
        Objects.requireNonNull(shownRequestBody, "shownRequestBody");
        Objects.requireNonNull(shownResponseBody, "shownResponseBody");
        Objects.requireNonNull(elapsed, "elapsed");
    }
}
