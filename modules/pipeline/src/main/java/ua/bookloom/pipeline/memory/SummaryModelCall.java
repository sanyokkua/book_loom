package ua.bookloom.pipeline.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.JsonReplies;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The one model call that writes a chapter's summary. It only asks and reads; whether an unreadable reply or a failed
 * call is worth a warning, and what is kept, is the keeper's decision.
 */
@Slf4j
@RequiredArgsConstructor
final class SummaryModelCall {

    private static final int ASKED_CHARS = 160;

    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final CallFrame frame;
    private final ModelCalls calls;

    /**
     * Asks the model to fold a chapter into the running summary.
     *
     * @param previousSummary the summary so far, empty for the first chapter; never null
     * @param chapterSource the chapter's accepted source texts, one per line; never null
     * @param chapterTarget the chapter's accepted targets, one per line; never null
     * @return the summary's target text, empty when the reply is not JSON or holds no target text; or the call's error
     */
    Result<Optional<String>> summarize(
            final String previousSummary, final String chapterSource, final String chapterTarget) {
        final List<ChatMessage> messages = messagesFor(previousSummary, chapterSource, chapterTarget);
        final ChatRequest request = ChatRequests.build(PromptName.SUMMARY, messages, OutputLimit.forSummary(), false);
        log.trace("Summary call messages {}", messages);
        final String asked = excerpt(chapterSource);
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.SUMMARY, List.of(), request, CallDescriptor.asking(PromptName.SUMMARY, List.of(asked)));
        if (reply.isErr()) {
            return Result.err(Objects.requireNonNull(reply.error(), "error"));
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Summary call reply {}", content);
        final Optional<String> target = targetOf(content);
        target.ifPresent(summary -> calls.answered(Map.of(asked, summary)));
        return Result.ok(target);
    }

    // The chapter as one short line: the dialog names what was summarized, not the whole chapter.
    private static String excerpt(final String chapterSource) {
        final String line = chapterSource.replaceAll("\\s+", " ").strip();
        return line.length() <= ASKED_CHARS ? line : line.substring(0, ASKED_CHARS) + "…";
    }

    private Optional<String> targetOf(final String content) {
        return JsonReplies.tolerant(mapper, content)
                .map(root -> root.path("summary").path("target"))
                .filter(JsonNode::isTextual)
                .map(node -> node.asText().strip())
                .filter(target -> !target.isEmpty());
    }

    private List<ChatMessage> messagesFor(
            final String previousSummary, final String chapterSource, final String chapterTarget) {
        final String system = templates.renderSystem(PromptName.SUMMARY, frame).strip();
        final String user = templates
                .renderUser(
                        PromptName.SUMMARY,
                        Map.of(
                                "previousSummary",
                                previousSummary,
                                "chapterSource",
                                chapterSource,
                                "chapterTarget",
                                chapterTarget))
                .strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }
}
