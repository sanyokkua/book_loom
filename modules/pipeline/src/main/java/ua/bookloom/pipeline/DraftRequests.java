package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.GateNotes;
import ua.bookloom.pipeline.prompt.OutputLimit;

/**
 * The requests of the draft step — the first draft and its two repairs — built in one place, so the step that sends
 * them and a prompt eval that only needs them cannot build different ones. A request is the one the step sends before
 * the run's seam sizes it to the window.
 */
@Slf4j
public final class DraftRequests {

    private final GateFunction gate;
    private final DraftPromptBuilder promptBuilder;
    private final DraftReplyParser replyParser;

    DraftRequests(final GateFunction gate, final DraftPromptBuilder promptBuilder, final DraftReplyParser replyParser) {
        this.gate = gate;
        this.promptBuilder = promptBuilder;
        this.replyParser = replyParser;
    }

    /**
     * The request of a segment's first draft.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null context the draft is shown
     * @param mask the non-null spans hidden in the segment
     * @return the draft's request
     */
    public ChatRequest draft(final Segment segment, final DraftContext context, final ProtectedMask mask) {
        return build(DraftAttempt.of(segment, context, mask), DraftStep.DRAFT, "", "");
    }

    /**
     * The request of the structural repair a reply that is not the required JSON object is sent, with the diagnostic
     * the reply reader gives for it.
     *
     * @param segment the non-null segment being drafted
     * @param context the non-null context the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @param rejectedReply the non-null raw reply the reader refused
     * @return the repair's request
     */
    public ChatRequest structuralRepair(
            final Segment segment, final DraftContext context, final ProtectedMask mask, final String rejectedReply) {
        final String diagnostic =
                replyParser.parse(rejectedReply, segment.masked()).diagnostic();
        return build(DraftAttempt.of(segment, context, mask), DraftStep.STRUCTURAL_REPAIR, rejectedReply, diagnostic);
    }

    /**
     * The request of the placeholder repair for a reply the gate refuses and no deterministic restore saves.
     *
     * @param segment the non-null segment being drafted
     * @param context the non-null context the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @param rejectedTarget the non-null target text the model gave, before its whitespace is restored
     * @return the repair's request, or empty when the gate accepts the target or restoring what it lost is enough
     */
    public Optional<ChatRequest> placeholderRepair(
            final Segment segment, final DraftContext context, final ProtectedMask mask, final String rejectedTarget) {
        final DraftAttempt attempt = DraftAttempt.of(segment, context, mask);
        final String rejected = WhitespaceRestoration.restore(segment.masked(), rejectedTarget.strip());
        if (gate.restore(segment, rejected) instanceof GateResult.GateFailed failed
                && !(gate.restoreRepairing(segment, rejected, PlaceholderRepair.RESTORE_MISSING)
                        instanceof GateResult.Restored)) {
            return Optional.of(
                    build(attempt, DraftStep.PLACEHOLDER_REPAIR, rejected, placeholderNote(attempt, rejected, failed)));
        }
        log.debug("No placeholder repair needed segmentId={}", segment.id());
        return Optional.empty();
    }

    ChatRequest build(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        Objects.requireNonNull(attempt, "attempt");
        final OutputLimit limit = OutputLimit.forSource(
                attempt.shownText(), promptBuilder.sourceLanguage(), promptBuilder.targetLanguage());
        final boolean lower = step == DraftStep.DRAFT && attempt.lowerTemperature();
        final ChatRequest request =
                ChatRequests.build(step.promptName(), messagesFor(attempt, step, rejected, diagnostic), limit, lower);
        log.debug(
                "Built chat request segmentId={} maskedLength={} messageCount={} expectedTokens={} capTokens={}",
                attempt.segment().id(),
                attempt.shownText().length(),
                request.messages().size(),
                request.expectedOutputTokens() == null ? "none" : request.expectedOutputTokens(),
                request.maxOutputTokens() == null ? "none" : request.maxOutputTokens());
        return request;
    }

    /**
     * What the shown call of a draft step names besides its request: the segment's source and the prompt's parts, built
     * from the same values as {@link #build}'s messages.
     */
    CallDescriptor descriptor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        Objects.requireNonNull(attempt, "attempt");
        return CallDescriptor.of(
                step.promptName(),
                DisplayText.of(attempt.segment().masked()),
                () -> promptBuilder.sectionsFor(
                        step,
                        attempt.context(),
                        attempt.shownText(),
                        attempt.extraInstruction(),
                        rejected,
                        diagnostic));
    }

    private List<ChatMessage> messagesFor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final Segment segment = attempt.segment();
        final DraftContext context = attempt.context();
        final String shown = attempt.shownText();
        final String extra = attempt.extraInstruction();
        return switch (step) {
            case DRAFT -> promptBuilder.messagesFor(segment, context, shown, extra);
            case STRUCTURAL_REPAIR ->
                promptBuilder.messagesForStructuredRepair(segment, context, shown, extra, rejected, diagnostic);
            case PLACEHOLDER_REPAIR ->
                promptBuilder.messagesForPlaceholderRepair(
                        segment, context, shown, extra, rejected, diagnostic.isEmpty() ? null : diagnostic);
        };
    }

    static String placeholderNote(
            final DraftAttempt attempt, final String rejectedTarget, final GateResult.GateFailed failed) {
        final Segment segment = attempt.segment();
        return GateNotes.describe(
                attempt.shownText(),
                rejectedTarget,
                segment.pairs(),
                segment.lineBreakTokens(),
                failed.finding().note());
    }
}
