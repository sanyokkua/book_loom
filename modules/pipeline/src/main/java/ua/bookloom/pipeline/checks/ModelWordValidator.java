package ua.bookloom.pipeline.checks;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The optional model-based {@link WordValidator}: one extra structured call over a batch of finished
 * target texts that lists the words that are not real words, each with a quoted phrase, and code that keeps only the
 * words the texts really hold. It costs a call and trusts a model's idea of a word, so nothing binds it by default;
 * it is built explicitly, and used by the garbled-word eval.
 */
@Slf4j
public final class ModelWordValidator implements WordValidator {

    private final PromptTemplates templates;
    private final ObjectMapper mapper;
    private final ModelCalls calls;
    private final CallFrame frame;

    /**
     * Creates a validator that asks the given seam.
     *
     * @param templates the prompt templates; never null
     * @param mapper the tolerant JSON mapper; never null
     * @param calls the seam every model call goes through; never null
     * @param frame the language pair of the system message; never null
     */
    public ModelWordValidator(
            final PromptTemplates templates, final ObjectMapper mapper, final ModelCalls calls, final CallFrame frame) {
        this.templates = Objects.requireNonNull(templates, "templates");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.frame = Objects.requireNonNull(frame, "frame");
    }

    @Override
    public List<CheckFinding> find(final String target, final String targetLanguage) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetLanguage, "targetLanguage");
        return findAll(List.of(target)).getFirst();
    }

    /**
     * Checks a batch of texts in one call.
     *
     * @param targets the display texts, in order; never null
     * @return per text the verified findings; always as many lists as texts, all empty when the call failed, because
     *     a check that cannot answer says nothing rather than blocking the book
     */
    public List<List<CheckFinding>> findAll(final List<String> targets) {
        Objects.requireNonNull(targets, "targets");
        log.debug("Garbled-word check texts={}", targets.size());
        if (targets.isEmpty()) {
            return List.of();
        }
        final ChatRequest request = request(targets);
        final Result<ChatResponse> reply = calls.call(CallKind.REVIEW, null, request);
        if (reply.isErr()) {
            log.warn(
                    "Garbled-word call failed code={}; no words reported",
                    Objects.requireNonNull(reply.error()).code());
            return emptyFor(targets);
        }
        final String content = Objects.requireNonNull(reply.data(), "reply").content();
        log.trace("Garbled-word reply {}", content);
        return SuspiciousWordsReply.read(mapper, content, targets);
    }

    private ChatRequest request(final List<String> targets) {
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            lines.add((i + 1) + ". " + targets.get(i).replaceAll("\\s+", " ").strip());
        }
        final String system =
                templates.renderSystem(PromptName.SUSPICIOUS_WORDS, frame).strip();
        final String user = templates
                .renderUser(PromptName.SUSPICIOUS_WORDS, Map.of("items", String.join("\n", lines)))
                .strip();
        log.trace("Garbled-word user message {}", user);
        return ChatRequests.build(
                PromptName.SUSPICIOUS_WORDS,
                List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user)),
                OutputLimit.forSuspiciousWords(targets.size()),
                false);
    }

    private static List<List<CheckFinding>> emptyFor(final List<String> targets) {
        return targets.stream().map(text -> List.<CheckFinding>of()).toList();
    }
}
