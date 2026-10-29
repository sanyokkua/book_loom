package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.llm.provider.CheckReply.Reasoning;

/**
 * Reads whether an OpenAI-shaped reply shows reasoning: a reasoning field with text (Ollama's {@code reasoning},
 * LM Studio's and llama-server's {@code reasoning_content}), a think block in the content, or counted reasoning tokens.
 * The cleaned answer has lost all three, so the thinking check reads the body as it came.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReasoningSign {

    private static final String THINK_OPEN = "<think>";

    static Reasoning in(final ObjectMapper mapper, final String body) {
        try {
            final JsonNode root = mapper.readTree(body);
            if (root == null) {
                return Reasoning.UNKNOWN;
            }
            final JsonNode message = root.path("choices").path(0).path("message");
            final boolean field = hasText(message.path("reasoning")) || hasText(message.path("reasoning_content"));
            final boolean block = message.path("content").asText("").contains(THINK_OPEN);
            final int tokens = root.path("usage")
                    .path("completion_tokens_details")
                    .path("reasoning_tokens")
                    .asInt(0);
            log.debug("Reply reasoning read field={} thinkBlock={} reasoningTokens={}", field, block, tokens);
            return field || block || tokens > 0 ? Reasoning.SHOWN : Reasoning.NONE;
        } catch (JsonProcessingException unreadable) {
            log.debug("Reply reasoning not read: the body is not JSON");
            return Reasoning.UNKNOWN;
        }
    }

    private static boolean hasText(final JsonNode node) {
        return node.isTextual() && !node.asText().isBlank();
    }
}
