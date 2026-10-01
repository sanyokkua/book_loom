package ua.bookloom.api.pipeline;

import org.jspecify.annotations.Nullable;

/**
 * The size of a request sent to the model, without its text: enough to tell a long prompt from a stalled server.
 *
 * @param messageChars the characters of every message of the request together, zero or more
 * @param contextWindow the context-window size the request asks for ({@code num_ctx}), or null for the provider's own
 * @param maxOutputTokens the cap on the reply ({@code num_predict}), or null when the request sets none
 */
public record RequestSummary(
        int messageChars,
        @Nullable Integer contextWindow,
        @Nullable Integer maxOutputTokens) {

    /** Rejects a negative character count. */
    public RequestSummary {
        if (messageChars < 0) {
            throw new IllegalArgumentException("messageChars must not be negative: " + messageChars);
        }
    }
}
