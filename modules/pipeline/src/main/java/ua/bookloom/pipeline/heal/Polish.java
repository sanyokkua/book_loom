package ua.bookloom.pipeline.heal;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The optional monolingual smoothing pass run only when {@link Borderline#isBorderline} holds for an improved
 * target: it shows the source under {@code [Source]}, like every other repair call, only so the smoothing cannot
 * drift the meaning (design D8; {@code specs/quality-gates/spec.md} "A near miss is polished").
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class Polish {

    private final PromptTemplates templates;
    private final DraftReplyParser replyParser;

    /**
     * Smooths one segment's candidate target for fluency without changing its meaning.
     *
     * @param segment the segment being polished, read here only for its id
     * @param frame the run's language pair, style sheet and foreign-passage policy
     * @param maskedSource the segment's masked source, shown under {@code [Source]}
     * @param maskedTarget the candidate target being polished, sent as the sole {@code <Text>} block
     * @param calls the seam the call is sent through
     * @return the model's reply classified into a {@link RepairReply}, or the call's own error for the job to route
     */
    public Result<RepairReply> polish(
            final Segment segment,
            final CallFrame frame,
            final String maskedSource,
            final String maskedTarget,
            final ModelCalls calls) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedTarget, "maskedTarget");
        Objects.requireNonNull(calls, "calls");
        log.debug("Polishing segment id={}", segment.id());
        try {
            return callPolish(segment, frame, maskedSource, maskedTarget, calls);
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Polish failed", "The model could not polish this segment.", null, cause);
            log.error("Unexpected polish failure segment={} code={}", segment.id(), error.code(), cause);
            return Result.err(error);
        }
    }

    private Result<RepairReply> callPolish(
            final Segment segment,
            final CallFrame frame,
            final String maskedSource,
            final String maskedTarget,
            final ModelCalls calls) {
        final List<ChatMessage> messages = messagesFor(frame, maskedSource, maskedTarget);
        final int allowance = TokenEstimator.outputAllowance(
                DisplayText.of(maskedSource), frame.sourceLanguage(), frame.targetLanguage());
        final ChatRequest request =
                ChatRequests.build(PromptName.POLISH, messages, allowance > 0 ? allowance : null, false);
        logTraceMessages(request);
        final Result<ChatResponse> reply = calls.call(CallKind.POLISH, segment.id(), request);
        logTraceReply(reply);
        final Result<RepairReply> outcome = RepairReplies.read(reply, replyParser);
        logOutcome(segment.id(), outcome);
        return outcome;
    }

    private List<ChatMessage> messagesFor(final CallFrame frame, final String maskedSource, final String maskedTarget) {
        final String system = templates
                .renderSystem(PromptName.POLISH, frame.systemSlotValues())
                .strip();
        final Map<String, String> userValues = new HashMap<>();
        userValues.put("source", maskedSource);
        userValues.put("text", maskedTarget);
        final String user = templates.renderUser(PromptName.POLISH, userValues).strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    private static void logTraceMessages(final ChatRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("Polish messages {}", request.messages());
        }
    }

    private static void logTraceReply(final Result<ChatResponse> reply) {
        if (log.isTraceEnabled() && reply.isOk()) {
            log.trace(
                    "Polish raw reply {}", Objects.requireNonNull(reply.data()).content());
        }
    }

    private static void logOutcome(final String segmentId, final Result<RepairReply> outcome) {
        if (outcome.isErr()) {
            log.debug(
                    "Polish reply segmentId={} outcome=error code={}",
                    segmentId,
                    Objects.requireNonNull(outcome.error()).code());
            return;
        }
        switch (Objects.requireNonNull(outcome.data())) {
            case RepairReply.Rewritten rewritten ->
                log.debug(
                        "Polish reply segmentId={} outcome=Rewritten targetLength={}",
                        segmentId,
                        rewritten.maskedTarget().length());
            case RepairReply.Malformed malformed ->
                log.debug(
                        "Polish reply segmentId={} outcome=Malformed diagnostic={}", segmentId, malformed.diagnostic());
            case RepairReply.FlagNow flagNow ->
                log.debug(
                        "Polish reply segmentId={} outcome=FlagNow code={}",
                        segmentId,
                        flagNow.error().code());
        }
    }
}
