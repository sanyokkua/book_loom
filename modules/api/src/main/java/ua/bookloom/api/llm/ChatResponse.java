package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Text and termination classification returned by a chat model.
 *
 * @param content the response text
 * @param finishReason how the model stopped producing text
 * @param usage the token counts and generation time the provider reported, or null when not reported
 * @param rawBody the reply body as the provider sent it, cut when very long, for the Detailed view of a call; null
 *     when the client keeps none. Book text lives in it, so it is shown to the person and logged at TRACE only.
 * @param served who answered and what it cost, or null when the reply said neither
 */
public record ChatResponse(
        String content,
        FinishReason finishReason,
        @Nullable TokenUsage usage,
        @Nullable String rawBody,
        @Nullable ServedCall served) {

    /**
     * Preserves the construction form for callers with no served provider or cost.
     *
     * @param content the response text
     * @param finishReason how the model stopped producing text
     * @param usage the reported usage, or null
     * @param rawBody the reply body, or null
     */
    public ChatResponse(
            String content, FinishReason finishReason, @Nullable TokenUsage usage, @Nullable String rawBody) {
        this(content, finishReason, usage, rawBody, null);
    }

    /**
     * Preserves the construction form for callers with no raw body.
     *
     * @param content the response text
     * @param finishReason how the model stopped producing text
     * @param usage the reported usage, or null
     */
    public ChatResponse(String content, FinishReason finishReason, @Nullable TokenUsage usage) {
        this(content, finishReason, usage, null);
    }

    /**
     * Preserves the original construction form for callers with no reported usage figures.
     *
     * @param content the response text
     * @param finishReason how the model stopped producing text
     */
    public ChatResponse(String content, FinishReason finishReason) {
        this(content, finishReason, null);
    }

    /**
     * Rejects a response that cannot tell its caller why it ended.
     */
    public ChatResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(finishReason, "finishReason");
    }
}
