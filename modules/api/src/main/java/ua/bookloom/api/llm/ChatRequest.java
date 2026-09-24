package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An ordered conversation sent to a {@link ChatModel}.
 *
 * @param messages the conversation in wire order; never null and defensively copied
 * @param temperature the sampling temperature for this call, or null when provider defaults should apply
 * @param responseFormat the requested structured response format, or null when no format is requested
 * @param reasoningEnabled null when the provider should use its default, false when supported reasoning output
 *     should be disabled
 */
public record ChatRequest(
        List<ChatMessage> messages,
        @Nullable Double temperature,
        @Nullable ResponseFormat responseFormat,
        @Nullable Boolean reasoningEnabled) {

    /**
     * Preserves the original message-only construction form, with no per-call settings.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     */
    public ChatRequest(List<ChatMessage> messages) {
        this(messages, null, null, null);
    }

    /**
     * Preserves the original per-call settings construction form without a reasoning control.
     *
     * @param messages the conversation in wire order; never null and defensively copied
     * @param temperature the sampling temperature for this call, or null when provider defaults should apply
     * @param responseFormat the requested structured response format, or null when no format is requested
     */
    public ChatRequest(
            List<ChatMessage> messages, @Nullable Double temperature, @Nullable ResponseFormat responseFormat) {
        this(messages, temperature, responseFormat, null);
    }

    /**
     * Keeps a request independent from the mutable collection supplied by its caller.
     */
    public ChatRequest {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }
}
