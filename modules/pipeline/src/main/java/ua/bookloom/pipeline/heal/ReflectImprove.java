package ua.bookloom.pipeline.heal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
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
 * The self-heal path for a vague quality concern with no concrete finding — a low confidence, a judge score below
 * τ_judge with no finding, or an unreadable judge reply: a reflection critique read back as a tolerant issue list,
 * then a rewrite that consumes it (design D8; {@code specs/quality-gates/spec.md} "Repair a failing segment within
 * the dial's repair budget before flagging it" — "A low score with no finding goes to reflect and improve").
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ReflectImprove {

    private final PromptTemplates templates;
    private final DraftReplyParser replyParser;
    private final ObjectMapper mapper;

    /**
     * Critiques one segment's candidate target, reading the reply into a tolerant issue list.
     *
     * @param segment the segment being critiqued, read here only for its id
     * @param frame the run's language pair, style sheet and foreign-passage policy
     * @param maskedSource the segment's masked source, shown under {@code [Source]}
     * @param maskedTarget the candidate target under critique, sent as the sole {@code <Text>} block
     * @param calls the seam the call is sent through
     * @return the critique's issues — empty when the reply carried none, was unparseable, or the call itself
     *     answered {@link ErrorCode#emptyCompletion}/{@link ErrorCode#contextWindow}; the call's own error,
     *     unwrapped, for any other call failure so task 8.6 can end the step with it
     */
    public Result<List<String>> reflect(
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
        log.debug("Reflecting on segment id={}", segment.id());
        try {
            return callReflect(segment.id(), frame, maskedSource, maskedTarget, calls);
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Reflect failed", "The model could not critique this segment.", null, cause);
            log.error("Unexpected reflect failure segment={} code={}", segment.id(), error.code(), cause);
            return Result.err(error);
        }
    }

    /**
     * Rewrites one segment's candidate target to address reflect's issues.
     *
     * @param segment the segment being improved, read here only for its id
     * @param frame the run's language pair, style sheet and foreign-passage policy
     * @param maskedSource the segment's masked source, shown under {@code [Source]}
     * @param maskedTarget the candidate target being improved, sent as the sole {@code <Text>} block
     * @param issues reflect's issues, rendered one per line; empty runs improve with no critique
     * @param calls the seam the call is sent through
     * @return the model's reply classified into a {@link RepairReply}, or the call's own error for the job to route
     */
    public Result<RepairReply> improve(
            final Segment segment,
            final CallFrame frame,
            final String maskedSource,
            final String maskedTarget,
            final List<String> issues,
            final ModelCalls calls) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedTarget, "maskedTarget");
        Objects.requireNonNull(issues, "issues");
        Objects.requireNonNull(calls, "calls");
        log.debug("Improving segment id={} issueCount={}", segment.id(), issues.size());
        try {
            return callImprove(segment, frame, maskedSource, maskedTarget, issues, calls);
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Improve failed", "The model could not improve this segment.", null, cause);
            log.error("Unexpected improve failure segment={} code={}", segment.id(), error.code(), cause);
            return Result.err(error);
        }
    }

    private Result<List<String>> callReflect(
            final String segmentId,
            final CallFrame frame,
            final String maskedSource,
            final String maskedTarget,
            final ModelCalls calls) {
        final List<ChatMessage> messages = reflectMessages(frame, maskedSource, maskedTarget);
        final ChatRequest request = ChatRequests.build(PromptName.REFLECT, messages, null, false);
        logTraceMessages("Reflect", request);
        final Result<ChatResponse> reply = calls.call(CallKind.REFLECT, segmentId, request);
        final Result<List<String>> outcome = readIssues(reply);
        logReflectOutcome(segmentId, outcome);
        return outcome;
    }

    private Result<RepairReply> callImprove(
            final Segment segment,
            final CallFrame frame,
            final String maskedSource,
            final String maskedTarget,
            final List<String> issues,
            final ModelCalls calls) {
        final List<ChatMessage> messages = improveMessages(frame, maskedSource, maskedTarget, issues);
        final int allowance = TokenEstimator.outputAllowance(
                DisplayText.of(maskedSource), frame.sourceLanguage(), frame.targetLanguage());
        final ChatRequest request =
                ChatRequests.build(PromptName.IMPROVE, messages, allowance > 0 ? allowance : null, false);
        logTraceMessages("Improve", request);
        final Result<ChatResponse> reply = calls.call(CallKind.IMPROVE, segment.id(), request);
        logTraceReply("Improve", reply);
        final Result<RepairReply> outcome = RepairReplies.read(reply, replyParser);
        logImproveOutcome(segment.id(), outcome);
        return outcome;
    }

    private List<ChatMessage> reflectMessages(
            final CallFrame frame, final String maskedSource, final String maskedTarget) {
        final String system = templates
                .renderSystem(PromptName.REFLECT, frame.systemSlotValues())
                .strip();
        final Map<String, String> userValues = new HashMap<>();
        userValues.put("source", maskedSource);
        userValues.put("text", maskedTarget);
        final String user = templates.renderUser(PromptName.REFLECT, userValues).strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    private List<ChatMessage> improveMessages(
            final CallFrame frame, final String maskedSource, final String maskedTarget, final List<String> issues) {
        final String system = templates
                .renderSystem(PromptName.IMPROVE, frame.systemSlotValues())
                .strip();
        final Map<String, String> userValues = new HashMap<>();
        userValues.put("source", maskedSource);
        userValues.put("text", maskedTarget);
        userValues.put("issues", String.join("\n", issues));
        final String user = templates.renderUser(PromptName.IMPROVE, userValues).strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    private Result<List<String>> readIssues(final Result<ChatResponse> reply) {
        if (reply.isErr()) {
            return routeReflectFailure(Objects.requireNonNull(reply.error()));
        }
        final ChatResponse response = Objects.requireNonNull(reply.data());
        if (log.isTraceEnabled()) {
            log.trace("Reflect raw reply {}", response.content());
        }
        return response.content().isBlank() ? Result.ok(List.of()) : Result.ok(parseIssues(response.content()));
    }

    private static Result<List<String>> routeReflectFailure(final AppError error) {
        return error.code() == ErrorCode.emptyCompletion || error.code() == ErrorCode.contextWindow
                ? Result.ok(List.of())
                : Result.err(error);
    }

    private List<String> parseIssues(final String replyText) {
        try {
            final JsonNode root = mapper.readTree(replyText);
            final JsonNode issues = root == null ? null : root.path("issues");
            if (issues == null || !issues.isArray()) {
                return List.of();
            }
            final List<String> result = new ArrayList<>();
            issues.forEach(node -> addIssue(result, node));
            return result;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private static void addIssue(final List<String> issues, final JsonNode node) {
        final String text = issueText(node);
        if (!text.isBlank()) {
            issues.add(text);
        }
    }

    private static String issueText(final JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        final String note = node.path("note").asText("");
        final String suggestion = node.path("suggestion").asText("");
        if (note.isEmpty()) {
            return suggestion;
        }
        return suggestion.isEmpty() ? note : note + " — " + suggestion;
    }

    private static void logTraceMessages(final String label, final ChatRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("{} messages {}", label, request.messages());
        }
    }

    private static void logTraceReply(final String label, final Result<ChatResponse> reply) {
        if (log.isTraceEnabled() && reply.isOk()) {
            log.trace(
                    "{} raw reply {}",
                    label,
                    Objects.requireNonNull(reply.data()).content());
        }
    }

    private static void logReflectOutcome(final String segmentId, final Result<List<String>> outcome) {
        if (outcome.isErr()) {
            log.debug(
                    "Reflect reply segmentId={} outcome=error code={}",
                    segmentId,
                    Objects.requireNonNull(outcome.error()).code());
            return;
        }
        log.debug(
                "Reflect reply segmentId={} issueCount={}",
                segmentId,
                Objects.requireNonNull(outcome.data()).size());
    }

    private static void logImproveOutcome(final String segmentId, final Result<RepairReply> outcome) {
        if (outcome.isErr()) {
            log.debug(
                    "Improve reply segmentId={} outcome=error code={}",
                    segmentId,
                    Objects.requireNonNull(outcome.error()).code());
            return;
        }
        switch (Objects.requireNonNull(outcome.data())) {
            case RepairReply.Rewritten rewritten ->
                log.debug(
                        "Improve reply segmentId={} outcome=Rewritten targetLength={}",
                        segmentId,
                        rewritten.maskedTarget().length());
            case RepairReply.Malformed malformed ->
                log.debug(
                        "Improve reply segmentId={} outcome=Malformed diagnostic={}",
                        segmentId,
                        malformed.diagnostic());
            case RepairReply.FlagNow flagNow ->
                log.debug(
                        "Improve reply segmentId={} outcome=FlagNow code={}",
                        segmentId,
                        flagNow.error().code());
        }
    }
}
