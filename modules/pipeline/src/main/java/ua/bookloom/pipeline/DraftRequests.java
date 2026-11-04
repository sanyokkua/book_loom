package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.OutputLimit;

/**
 * The requests of a segment's draft — the first draft, its format re-ask and its one repair — built in one place, so the step that sends
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
        final String diagnostic = replyParser
                .parse(rejectedReply, segment.masked(), promptBuilder.targetLanguage())
                .diagnostic();
        return build(DraftAttempt.of(segment, context, mask), DraftStep.STRUCTURAL_REPAIR, rejectedReply, diagnostic);
    }

    /**
     * The request of the one repair: the draft's prompt, the previous attempt and its defects, sent with the retry
     * seed.
     *
     * @param segment the non-null segment being repaired
     * @param context the non-null context the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @param previousAttempt the non-null masked text the repair rewrites
     * @param defects the non-null findings to fix, each with its explanation and the span it quotes
     * @return the repair's request
     */
    public ChatRequest repair(
            final Segment segment,
            final DraftContext context,
            final ProtectedMask mask,
            final String previousAttempt,
            final List<QaFinding> defects) {
        return build(DraftAttempt.of(segment, context, mask), DraftStep.REPAIR, previousAttempt, defectLines(defects));
    }

    /**
     * The request of the one repair for a target whose formatting marks the code cannot put back.
     *
     * @param segment the non-null segment being drafted
     * @param context the non-null context the draft was shown
     * @param mask the non-null spans hidden in the segment
     * @param rejectedTarget the non-null target text the model gave, before its whitespace is restored
     * @return the repair's request naming the gate's finding, or empty when the gate or a code repair accepts it
     */
    public Optional<ChatRequest> repairOfLostMarks(
            final Segment segment, final DraftContext context, final ProtectedMask mask, final String rejectedTarget) {
        final String rejected = WhitespaceRestoration.restore(segment.masked(), rejectedTarget.strip());
        if (gate.restore(segment, rejected) instanceof GateResult.GateFailed failed
                && !restoresByCode(segment, rejected)) {
            return Optional.of(repair(segment, context, mask, rejected, List.of(failed.finding())));
        }
        log.debug("No repair of lost marks needed segmentId={}", segment.id());
        return Optional.empty();
    }

    private boolean restoresByCode(final Segment segment, final String rejected) {
        return gate.restoreRepairing(segment, rejected, PlaceholderRepair.RESTORE_MISSING)
                        instanceof GateResult.Restored
                || gate.restoreRepairing(segment, rejected, PlaceholderRepair.REWRAP_ALL)
                        instanceof GateResult.Restored;
    }

    /**
     * The defects a repair names, one {@code kind (severity): explanation} line each; the explanation quotes the span.
     *
     * @param defects the non-null findings
     * @return the lines joined by newlines; empty when there is none
     */
    static String defectLines(final List<QaFinding> defects) {
        return defects.stream()
                .map(finding -> finding.kind() + " (" + finding.severity() + "): " + finding.note())
                .collect(Collectors.joining("\n"));
    }

    ChatRequest build(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        Objects.requireNonNull(attempt, "attempt");
        final OutputLimit limit = OutputLimit.forSource(
                attempt.shownText(), promptBuilder.sourceLanguage(), promptBuilder.targetLanguage());
        final boolean lower = step == DraftStep.DRAFT && attempt.lowerTemperature();
        final ChatRequest built =
                ChatRequests.build(step.promptName(), messagesFor(attempt, step, rejected, diagnostic), limit, lower);
        final ChatRequest request = step == DraftStep.REPAIR ? built.withSeed(ChatRequests.RETRY_SEED) : built;
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
            case REPAIR -> promptBuilder.messagesForRepair(segment, context, shown, extra, rejected, diagnostic);
        };
    }
}
