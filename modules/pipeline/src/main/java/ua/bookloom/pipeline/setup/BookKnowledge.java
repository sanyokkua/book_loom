package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.pipeline.heal.SelfHealCalls;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/**
 * Asks the model what it knows of the book by its title and author and judges the answer
 * ({@link BookKnowledgeVerdict}). One small call, made only when the book has a title that is not its file name; the
 * year and publisher the book states are never sent, so the answer's year is the model's own.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class BookKnowledge {

    private static final int EXPECTED_TOKENS = 160;
    private static final int CAP_TOKENS = 320;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;

    /**
     * Asks, or skips the call when the book has no title or its title is the file stem.
     *
     * @param facts the book's metadata
     * @param fileStem the file name without its extension
     * @param frame the call frame
     * @param calls the model-call seam
     * @return the verdict, "unknown" with its reason when the call is skipped, fails or its answer does not stand; the
     *     call's own error only when the person stopped it ({@code cancelled}), which no later step may go on after
     */
    public Result<Knowledge> ask(
            final BookFacts facts, final String fileStem, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(fileStem, "fileStem");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        final Reason skipped = BookKnowledgeVerdict.skipReason(facts, fileStem);
        if (skipped != null) {
            log.debug("Book knowledge not asked: {}", skipped.label());
            return Result.ok(logged(Knowledge.unknown(skipped)));
        }
        final Result<ChatResponse> reply = send(facts, frame, calls);
        if (reply.isErr()) {
            final AppError error = Objects.requireNonNull(reply.error(), "error");
            if (error.code() == ErrorCode.cancelled) {
                log.info("Book knowledge question stopped by the person");
                return Result.err(error);
            }
            return Result.ok(logged(Knowledge.unknown(Reason.CALL_FAILED)));
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Book knowledge reply: {}", content);
        return Result.ok(judge(facts, fileStem, content));
    }

    /**
     * Judges an answer the model already gave, without asking again.
     *
     * @param facts the book's metadata
     * @param fileStem the file name without its extension
     * @param content the model's reply text, or null when the call gave none
     * @return the verdict; never null
     */
    public Knowledge judge(final BookFacts facts, final String fileStem, final @Nullable String content) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(fileStem, "fileStem");
        return logged(BookKnowledgeVerdict.judge(facts, fileStem, content == null ? null : read(content)));
    }

    private Result<ChatResponse> send(final BookFacts facts, final CallFrame frame, final ModelCalls calls) {
        final Map<String, String> user = new HashMap<>();
        user.put("title", Objects.requireNonNull(facts.title(), "title"));
        if (facts.author() != null) {
            user.put("author", facts.author());
        }
        final PromptName name = PromptName.BOOK_KNOWLEDGE;
        final ChatRequest request = ChatRequests.build(
                name,
                SelfHealCalls.messagesFor(templates, name, frame, user),
                new OutputLimit(EXPECTED_TOKENS, CAP_TOKENS),
                false);
        log.debug("Book knowledge asked: author given={}", facts.author() != null);
        return calls.callAbout(name.callKind(), List.of(), request, CallDescriptor.whole(name));
    }

    private static Knowledge logged(final Knowledge knowledge) {
        final Reason reason = knowledge.reason();
        final BookKnowledgeReply reply = knowledge.reply();
        if (reply != null) {
            log.info("Book knowledge: known, confidence {}", reply.confidence());
        } else if (reason != null) {
            log.info("Book knowledge: unknown, reason {}", reason.label());
        }
        return knowledge;
    }

    /** Reads the object out of any prose around it; null when there is none or it lacks a boolean {@code known}. */
    private @Nullable BookKnowledgeReply read(final String content) {
        final int from = content.indexOf('{');
        final int to = content.lastIndexOf('}');
        if (from < 0 || to <= from) {
            return null;
        }
        try {
            final JsonNode node = mapper.readTree(content.substring(from, to + 1));
            return node.path("known").isBoolean() ? replyOf(node) : null;
        } catch (JsonProcessingException cause) {
            log.debug("Book knowledge reply is not JSON: {}", cause.getOriginalMessage());
            return null;
        }
    }

    private static BookKnowledgeReply replyOf(final JsonNode node) {
        final JsonNode year = node.path("year");
        return new BookKnowledgeReply(
                node.path("known").asBoolean(),
                text(node, "title"),
                text(node, "author"),
                year.isIntegralNumber() && year.asInt() > 0 ? year.asInt() : null,
                text(node, "genre"),
                text(node, "audience"),
                text(node, "era"),
                text(node, "narration"),
                text(node, "summary"),
                node.path("confidence").isNumber() ? node.path("confidence").asDouble() : 0.0);
    }

    private static @Nullable String text(final JsonNode node, final String field) {
        final String value =
                node.path(field).isTextual() ? node.path(field).asText().strip() : "";
        return value.isEmpty() ? null : value;
    }
}
