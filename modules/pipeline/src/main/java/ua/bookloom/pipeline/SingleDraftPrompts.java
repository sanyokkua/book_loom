package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The old single-segment draft and its format correction, answered as one {@code {"target"}} object. No production
 * draft step uses it since the review retry is planned by the context builder (task 11.7); only the old prompt's tests
 * build it, and it goes with {@code DraftContext} (task 11.8). It sends no repair, so it has none.
 */
@Slf4j
final class SingleDraftPrompts implements DraftPrompts {

    private final DraftPromptBuilder promptBuilder;
    private final DraftReplyParser replyParser;

    SingleDraftPrompts(final DraftPromptBuilder promptBuilder, final DraftReplyParser replyParser) {
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.replyParser = Objects.requireNonNull(replyParser, "replyParser");
    }

    @Override
    public @Nullable String sourceLanguage() {
        return promptBuilder.sourceLanguage();
    }

    @Override
    public String targetLanguage() {
        return promptBuilder.targetLanguage();
    }

    @Override
    public ChatRequest build(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final OutputLimit limit = OutputLimit.forSource(attempt.shownText(), sourceLanguage(), targetLanguage());
        final boolean lower = step == DraftStep.DRAFT && attempt.lowerTemperature();
        log.debug("Single draft request segmentId={} step={}", attempt.segment().id(), step);
        return ChatRequests.build(promptName(step), messagesFor(attempt, step, rejected, diagnostic), limit, lower);
    }

    @Override
    public CallDescriptor descriptor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        return CallDescriptor.of(
                promptName(step),
                DisplayText.of(attempt.segment().masked()),
                () -> promptBuilder.sectionsFor(
                        step,
                        attempt.context(),
                        attempt.shownText(),
                        attempt.extraInstruction(),
                        rejected,
                        diagnostic));
    }

    @Override
    public ParsedReply parse(final String reply, final DraftAttempt attempt) {
        return replyParser.parse(reply, attempt.segment().masked(), targetLanguage());
    }

    @Override
    public ParsedReply parseRepair(final String reply, final DraftAttempt attempt) {
        return replyParser.parseRepair(reply, attempt.shownText(), targetLanguage());
    }

    @Override
    public boolean copiesExample(final String maskedTarget, final DraftAttempt attempt) {
        return false;
    }

    private static PromptName promptName(final DraftStep step) {
        return switch (step) {
            case DRAFT -> PromptName.DRAFT;
            case STRUCTURAL_REPAIR -> PromptName.STRUCTURAL_REPAIR;
            case REPAIR -> throw new IllegalStateException("A review retry sends no repair");
        };
    }

    private List<ChatMessage> messagesFor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        return switch (step) {
            case DRAFT ->
                promptBuilder.messagesFor(
                        attempt.segment(), attempt.context(), attempt.shownText(), attempt.extraInstruction());
            case STRUCTURAL_REPAIR ->
                promptBuilder.messagesForStructuredRepair(
                        attempt.segment(),
                        attempt.context(),
                        attempt.shownText(),
                        attempt.extraInstruction(),
                        rejected,
                        diagnostic);
            case REPAIR -> throw new IllegalStateException("A review retry sends no repair");
        };
    }
}
