package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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

    // A line shown for what a call asked is cut here so one long passage does not fill the dialog.
    private static final int ASKED_CHARS = 160;

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
        return ChatRequests.build(name, messages, limit, false);
    }

    /**
     * Sends a request as a whole-book call of its prompt's kind, announced like every model call, with what it asks
     * and, once answered, each field of the reply's object as the answer.
     *
     * @param request the request
     * @param name its prompt
     * @param calls the model-call seam
     * @param asked what the call asks, one line per thing, as a person reads it
     * @return the reply's text, or the call's error
     */
    Result<String> send(
            final ChatRequest request, final PromptName name, final ModelCalls calls, final List<String> asked) {
        log.debug("Setup call {} sent asked={}", name, asked.size());
        final Result<ChatResponse> reply =
                calls.callAbout(name.callKind(), List.of(), request, CallDescriptor.asking(name, asked));
        if (reply.isErr()) {
            return Result.err(Objects.requireNonNull(reply.error(), "error"));
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Setup call {} answered {}", name, content);
        calls.answered(fieldsOf(content));
        return Result.ok(content);
    }

    /**
     * The lines a call asked, from the values of its user template.
     *
     * @param user the user template's values, in the order they are shown
     * @return one {@code name: value} line per non-blank value, cut to a readable length
     */
    static List<String> askedOf(final Map<String, String> user) {
        return user.entrySet().stream()
                .filter(entry -> !entry.getValue().isBlank())
                .map(entry -> excerpt(entry.getKey() + ": " + entry.getValue()))
                .toList();
    }

    /**
     * One line of text cut to a readable length.
     *
     * @param text the text
     * @return the text on one line, with a cut one ending in an ellipsis
     */
    static String excerpt(final String text) {
        final String line = text.replaceAll("\\s+", " ").strip();
        return line.length() <= ASKED_CHARS ? line : line.substring(0, ASKED_CHARS) + "…";
    }

    // Each member of the reply's object as an answer line; nothing is shown for a reply with no object.
    private Map<String, @Nullable String> fieldsOf(final String content) {
        final JsonNode root = json(content);
        final Map<String, @Nullable String> fields = new LinkedHashMap<>();
        if (root == null) {
            return fields;
        }
        root.properties()
                .forEach(member -> fields.put(
                        member.getKey(),
                        member.getValue().isValueNode()
                                ? excerpt(member.getValue().asText())
                                : excerpt(member.getValue().toString())));
        return fields;
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
        } catch (java.io.IOException _) {
            log.debug("Setup reply is not JSON");
            return null;
        }
    }
}
