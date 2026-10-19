package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Text and termination classification returned by a chat model.
 *
 * @param content the response text
 * @param finishReason how the model stopped producing text
 * @param usage the token counts and generation time the provider reported, or null when not reported
 */
public record ChatResponse(
        String content, FinishReason finishReason, @Nullable TokenUsage usage) {

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
