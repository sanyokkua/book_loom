package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.pipeline.heal.SelfHealCalls;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** How every setup call is built, sent and read: one request shape, one announcement, one lenient JSON read. */
@Slf4j
@RequiredArgsConstructor
final class SetupCalls {

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    /**
     * The request of one setup call, in the book's frame, with reasoning off.
     *
     * @param book the book the call is about
     * @param name the call's prompt
     * @param user the user template's values
     * @param limit the reply's expected size and cap
     * @return the request; never null
     */
    ChatRequest requestOf(
            final SetupBook book, final PromptName name, final Map<String, String> user, final OutputLimit limit) {
        final List<ChatMessage> messages = SelfHealCalls.messagesFor(templates, name, book.frame(), user);
        return ChatRequests.build(name, messages, limit, false, book.frame().requestDefaults());
    }

    /**
     * Sends a request as a whole-book call of its prompt's kind, announced like every model call.
     *
     * @param request the request
     * @param name its prompt
     * @param calls the model-call seam
     * @return the reply's text, or the call's error
     */
    Result<String> send(final ChatRequest request, final PromptName name, final ModelCalls calls) {
        log.debug("Setup call {} sent", name);
        final Result<ChatResponse> reply =
                calls.callAbout(name.callKind(), List.of(), request, CallDescriptor.whole(name));
        if (reply.isErr()) {
            return Result.err(Objects.requireNonNull(reply.error(), "error"));
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Setup call {} answered {}", name, content);
        return Result.ok(content);
    }

    /**
     * The JSON object of a reply, read out of any prose around it.
     *
     * @param reply the reply's text
     * @return the object, or null when the reply holds none
     */
    @Nullable
    JsonNode json(final String reply) {
        final int from = reply.indexOf('{');
        final int to = reply.lastIndexOf('}');
        if (from < 0 || to <= from) {
            log.debug("Setup reply holds no object");
            return null;
        }
        try {
            return mapper.readTree(reply.substring(from, to + 1));
        } catch (java.io.IOException unreadable) {
            log.debug("Setup reply is not JSON");
            return null;
        }
    }
}
