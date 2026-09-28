package ua.bookloom.pipeline.heal;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.qa.CheckName;

/**
 * The single-segment self-heal call that names concrete findings and asks the model to fix exactly those and change
 * nothing else — one {@code <Text>} block holding only the text to rewrite (design D7), so a small model repairs a
 * single-target object reliably instead of an array keyed by id
 * ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair budget before flagging
 * it").
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class DirectedFix {

    private static final Set<String> EXPECTED_TOKEN_RAISED_BY =
            Set.of(CheckName.PLACEHOLDER.raisedBy(), CheckName.LOCKED_TERM.raisedBy(), CheckName.KEPT_RUN.raisedBy());

    private final PromptTemplates templates;
    private final DraftReplyParser replyParser;

    /**
     * Rewrites one segment's rejected target to fix its named findings in a single call.
     *
     * @param segment the segment being repaired, read here only for its id
     * @param frame the run's language pair, style sheet and foreign-passage policy
     * @param maskedSource the segment's masked source, shown under {@code [Source]} and, on a refusal finding, sent
     *     as the text to rewrite instead of {@code textToRewrite}
     * @param textToRewrite the rejected masked target, sent as the text to rewrite unless a finding was raised by
     *     the refusal gate
     * @param findings the concrete findings this segment failed, rendered one per line
     * @param calls the seam the call is sent through
     * @return the model's reply classified into a {@link RepairReply}, or the call's own error for the job to route
     */
    public Result<RepairReply> fix(
            final Segment segment,
            final CallFrame frame,
            final String maskedSource,
            final String textToRewrite,
            final List<QaFinding> findings,
            final ModelCalls calls) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(textToRewrite, "textToRewrite");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(calls, "calls");
        final boolean refusal = findings.stream().anyMatch(DirectedFix::isRefusal);
        final boolean includeExpectedTokens = findings.stream().anyMatch(DirectedFix::needsExpectedTokens);
        final String block = refusal ? maskedSource : textToRewrite;
        log.debug(
                "Fixing segment id={} findingKinds={} textSource={} expectedTokensIncluded={}",
                segment.id(),
                findingKinds(findings),
                refusal ? "source" : "rejected-target",
                includeExpectedTokens);
        try {
            return callAndRead(segment.id(), frame, maskedSource, block, findings, includeExpectedTokens, calls);
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Directed fix failed", "The model could not repair this segment.", null, cause);
            log.error("Unexpected directed fix failure segment={} code={}", segment.id(), error.code(), cause);
            return Result.err(error);
        }
    }

    private Result<RepairReply> callAndRead(
            final String segmentId,
            final CallFrame frame,
            final String maskedSource,
            final String block,
            final List<QaFinding> findings,
            final boolean includeExpectedTokens,
            final ModelCalls calls) {
        final List<ChatMessage> messages = messagesFor(frame, maskedSource, block, findings, includeExpectedTokens);
        final int allowance = TokenEstimator.outputAllowance(
                DisplayText.of(maskedSource), frame.sourceLanguage(), frame.targetLanguage());
        final ChatRequest request =
                ChatRequests.build(PromptName.DIRECTED_FIX, messages, allowance > 0 ? allowance : null, false);
        logTraceMessages(request);
        final Result<ChatResponse> reply = calls.call(CallKind.DIRECTED_FIX, segmentId, request);
        logTraceReply(reply);
        final Result<RepairReply> outcome = RepairReplies.read(reply, replyParser);
        logOutcome(segmentId, outcome);
        return outcome;
    }

    private List<ChatMessage> messagesFor(
            final CallFrame frame,
            final String maskedSource,
            final String block,
            final List<QaFinding> findings,
            final boolean includeExpectedTokens) {
        final String system = templates
                .renderSystem(PromptName.DIRECTED_FIX, frame.systemSlotValues())
                .strip();
        final Map<String, String> userValues = new HashMap<>();
        userValues.put("source", maskedSource);
        userValues.put("text", block);
        userValues.put("findings", renderFindings(findings));
        if (includeExpectedTokens) {
            userValues.put("expectedTokens", DraftPromptBuilder.expectedTokenSequence(maskedSource));
        }
        final String user =
                templates.renderUser(PromptName.DIRECTED_FIX, userValues).strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    private static String renderFindings(final List<QaFinding> findings) {
        return findings.stream()
                .map(finding -> finding.kind() + " (" + finding.severity() + "): " + finding.note())
                .collect(Collectors.joining("\n"));
    }

    private static boolean isRefusal(final QaFinding finding) {
        return CheckName.REFUSAL.raisedBy().equals(finding.raisedBy());
    }

    private static boolean needsExpectedTokens(final QaFinding finding) {
        return EXPECTED_TOKEN_RAISED_BY.contains(finding.raisedBy());
    }

    private static List<String> findingKinds(final List<QaFinding> findings) {
        return findings.stream().map(QaFinding::kind).toList();
    }

    private static void logTraceMessages(final ChatRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("Directed fix messages {}", request.messages());
        }
    }

    private static void logTraceReply(final Result<ChatResponse> reply) {
        if (log.isTraceEnabled() && reply.isOk()) {
            log.trace(
                    "Directed fix raw reply {}",
                    Objects.requireNonNull(reply.data()).content());
        }
    }

    private static void logOutcome(final String segmentId, final Result<RepairReply> outcome) {
        if (outcome.isErr()) {
            log.debug(
                    "Directed fix reply segmentId={} outcome=error code={}",
                    segmentId,
                    Objects.requireNonNull(outcome.error()).code());
            return;
        }
        switch (Objects.requireNonNull(outcome.data())) {
            case RepairReply.Rewritten rewritten ->
                log.debug(
                        "Directed fix reply segmentId={} outcome=Rewritten targetLength={}",
                        segmentId,
                        rewritten.maskedTarget().length());
            case RepairReply.Malformed malformed ->
                log.debug(
                        "Directed fix reply segmentId={} outcome=Malformed diagnostic={}",
                        segmentId,
                        malformed.diagnostic());
            case RepairReply.FlagNow flagNow ->
                log.debug(
                        "Directed fix reply segmentId={} outcome=FlagNow code={}",
                        segmentId,
                        flagNow.error().code());
        }
    }
}
