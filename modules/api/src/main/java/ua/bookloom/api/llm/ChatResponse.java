package ua.bookloom.api.llm;

import java.util.Objects;

/**
 * Text and termination classification returned by a chat model.
 *
 * @param content the response text
 * @param finishReason how the model stopped producing text
 */
public record ChatResponse(String content, FinishReason finishReason) {

    /**
     * Rejects a response that cannot tell its caller why it ended.
     */
    public ChatResponse {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(finishReason, "finishReason");
    }
}
