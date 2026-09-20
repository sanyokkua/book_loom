package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;

/**
 * An ordered conversation sent to a {@link ChatModel}.
 *
 * @param messages the conversation in wire order; never null and defensively copied
 */
public record ChatRequest(List<ChatMessage> messages) {

    /**
     * Keeps a request independent from the mutable collection supplied by its caller.
     */
    public ChatRequest {
        Objects.requireNonNull(messages, "messages");
        messages = List.copyOf(messages);
    }
}
