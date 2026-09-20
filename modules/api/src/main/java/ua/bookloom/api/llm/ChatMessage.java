package ua.bookloom.api.llm;

import java.util.Objects;

/**
 * One message in a provider-neutral chat conversation.
 *
 * @param role the speaker role
 * @param content the message text, including any masked document text
 */
public record ChatMessage(ChatRole role, String content) {

    /**
     * Rejects incomplete messages at the contract boundary.
     */
    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }
}
