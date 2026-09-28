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
import ua.bookloom.api.pipeline.CallKind;
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

    private static final String LABEL = "Polish";

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
        log.debug("Polishing segment id={} temperature={}", segment.id(), PromptName.POLISH.temperature(false));
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
        final ChatRequest request = ChatRequests.build(
                PromptName.POLISH, messages, SelfHealCalls.outputAllowance(maskedSource, frame), false);
        SelfHealCalls.logTraceMessages(log, LABEL, request);
        final Result<ChatResponse> reply = calls.call(CallKind.POLISH, segment.id(), request);
        SelfHealCalls.logTraceReply(log, LABEL, reply);
        final Result<RepairReply> outcome = RepairReplies.read(reply, replyParser);
        SelfHealCalls.logOutcome(log, LABEL, segment.id(), outcome);
        return outcome;
    }

    private List<ChatMessage> messagesFor(final CallFrame frame, final String maskedSource, final String maskedTarget) {
        final Map<String, String> userValues = new HashMap<>();
        userValues.put("source", maskedSource);
        userValues.put("text", maskedTarget);
        return SelfHealCalls.messagesFor(templates, PromptName.POLISH, frame, userValues);
    }
}
