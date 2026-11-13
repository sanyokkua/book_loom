package ua.bookloom.api.llm;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * One request of a model call as it leaves for the provider: a call that times out or fails transiently is sent again,
 * and each send is an attempt with its own clock.
 *
 * @param number the attempt, counted from one
 * @param maxAttempts how many attempts this call may make if each one stalls, at least {@code number}
 * @param timeout how long this attempt may wait for its reply, or null when the model sets no bound of its own
 * @param maxOutputTokens the attempt's output cap, which a retry after a timeout lowers, or null when it has none
 * @param requestBody the JSON body this attempt puts on the wire, with no header or credential, or null when the client
 *     cannot show one; book text lives in it, so it is shown to the person and logged at TRACE only
 */
public record CallAttempt(
        int number,
        int maxAttempts,
        @Nullable Duration timeout,
        @Nullable Integer maxOutputTokens,
        @Nullable String requestBody) {

    /** An attempt that carries no wire body. */
    public CallAttempt(
            final int number,
            final int maxAttempts,
            @Nullable final Duration timeout,
            @Nullable final Integer maxOutputTokens) {
        this(number, maxAttempts, timeout, maxOutputTokens, null);
    }

    /** Rejects an attempt numbered below one or beyond its own maximum. */
    public CallAttempt {
        if (number < 1 || maxAttempts < number) {
            throw new IllegalArgumentException("attempt " + number + " of " + maxAttempts);
        }
    }
}
