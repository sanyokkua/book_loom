package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.llm.dto.OpenAiChatResponse;

/**
 * Notices a reply whose output cap a thinking model spent on reasoning — no content, reasoning text, cut off at the
 * cap — and gives the one request to send again with a larger cap. A server that ignores {@code reasoning_effort}
 * answers a capped call this way, and an empty completion would flag the segment though the model can answer.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReasoningBudget {

    /** How many times the original cap the retry allows. */
    static final int RAISE_FACTOR = 4;

    private static final String LENGTH = "length";

    /**
     * The request to send once more with a raised cap.
     *
     * @param mapper the JSON reader; never null
     * @param body the reply body as received; never null
     * @param request the request that got it; never null
     * @return the request with four times its cap, bounded by its context window, if the reply spent its cap on
     *     reasoning and a larger cap is possible, or empty otherwise
     */
    static Optional<ChatRequest> raisedAfter(ObjectMapper mapper, String body, ChatRequest request) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(body, "body");
        final Integer cap = Objects.requireNonNull(request, "request").maxOutputTokens();
        if (cap == null || !reasoningUsedTheCap(mapper, body)) {
            return Optional.empty();
        }
        final Integer window = request.contextWindow();
        final int raised = window == null ? cap * RAISE_FACTOR : Math.min(cap * RAISE_FACTOR, window);
        log.debug("Reasoning cap check cap={} contextWindow={} raised={}", cap, window, raised);
        if (raised <= cap) {
            return Optional.empty();
        }
        log.warn(
                "The model spent its whole output cap of {} tokens on reasoning; asking once more with {}",
                cap,
                raised);
        return Optional.of(withCap(request, raised));
    }

    private static boolean reasoningUsedTheCap(ObjectMapper mapper, String body) {
        try {
            final OpenAiChatResponse decoded = mapper.readValue(body, OpenAiChatResponse.class);
            final List<OpenAiChatResponse.Choice> choices = decoded == null ? null : decoded.choices();
            final OpenAiChatResponse.@Nullable Choice choice =
                    choices == null || choices.isEmpty() ? null : choices.getFirst();
            if (choice == null || choice.message() == null) {
                return false;
            }
            final OpenAiChatResponse.Message message = Objects.requireNonNull(choice.message(), "message");
            return (message.content() == null || message.content().isBlank())
                    && message.hasReasoning()
                    && LENGTH.equals(choice.finishReason());
        } catch (JsonProcessingException unreadable) {
            log.debug("Reasoning cap check skipped: the reply is not JSON");
            return false;
        }
    }

    private static ChatRequest withCap(ChatRequest request, int cap) {
        return new ChatRequest(
                request.messages(),
                request.temperature(),
                request.responseFormat(),
                request.reasoningEnabled(),
                request.contextWindow(),
                request.expectedOutputTokens(),
                cap,
                request.seed(),
                request.callKind(),
                request.sampling());
    }
}
