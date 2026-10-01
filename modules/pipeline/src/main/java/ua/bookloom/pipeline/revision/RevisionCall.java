package ua.bookloom.pipeline.revision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.heal.QaEvaluation;
import ua.bookloom.pipeline.heal.RepairReplies;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.heal.SelfHealCalls;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * One revision call: the segment's masked target is the one {@code <Text>} block, its masked source sits under
 * {@code [Source]} outside it, and the characters whose gender is now known are named as facts. The reply is read like
 * every other repair reply and is kept only when it restores through the document gate, passes every hard gate and
 * fails no soft check outright.
 */
@Slf4j
@RequiredArgsConstructor
final class RevisionCall {

    private static final String LABEL = "Revision";

    private final PromptTemplates templates;
    private final DraftReplyParser replyParser;

    /**
     * Re-renders one segment.
     *
     * @param inputs what the pass read as it started
     * @param segment the opened book's segment
     * @param maskedTarget the target to revise, the document's own tokens in place
     * @param facts one line per character and its now-known gender
     * @param calls the seam the call is sent through
     * @return the revised target restored through the gate; empty when the reply was unreadable or refused by a gate
     *     or a check; or the call's own error, which ends the pass
     */
    Result<Optional<GateResult.Restored>> revise(
            final PassInputs inputs,
            final Segment segment,
            final String maskedTarget,
            final String facts,
            final ModelCalls calls) {
        final ChatRequest request = request(inputs, segment, maskedTarget, facts);
        SelfHealCalls.logTraceMessages(log, LABEL, request);
        final Result<ChatResponse> reply = calls.call(CallKind.REVISION, segment.id(), request);
        SelfHealCalls.logTraceReply(log, LABEL, reply);
        final Result<RepairReply> read = RepairReplies.read(reply, replyParser);
        SelfHealCalls.logOutcome(log, LABEL, segment.id(), read);
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        return switch (Objects.requireNonNull(read.data(), "read")) {
            case RepairReply.Rewritten rewritten -> gated(inputs, segment, rewritten.maskedTarget());
            case RepairReply.Malformed malformed -> Result.ok(Optional.empty());
            case RepairReply.FlagNow flagNow -> Result.ok(Optional.empty());
        };
    }

    private ChatRequest request(
            final PassInputs inputs, final Segment segment, final String maskedTarget, final String facts) {
        final Map<String, String> user = new HashMap<>();
        user.put("source", segment.masked());
        user.put("text", maskedTarget);
        user.put("resolvedFacts", facts);
        final List<ChatMessage> messages =
                SelfHealCalls.messagesFor(templates, PromptName.REVISION, inputs.frame(), user);
        return ChatRequests.build(
                PromptName.REVISION, messages, SelfHealCalls.outputLimit(segment.masked(), inputs.frame()), false);
    }

    private static Result<Optional<GateResult.Restored>> gated(
            final PassInputs inputs, final Segment segment, final String reply) {
        final String candidate = WhitespaceRestoration.restore(segment.masked(), reply);
        return switch (inputs.gate().restore(segment, candidate)) {
            case GateResult.Restored restored ->
                Result.ok(
                        passesChecks(inputs, segment, candidate, restored) ? Optional.of(restored) : Optional.empty());
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Revision refused segmentId={}: the gate failed raisedBy={}",
                        segment.id(),
                        failed.finding().raisedBy());
                yield Result.ok(Optional.empty());
            }
            case GateResult.StepError stepError -> Result.err(stepError.error());
        };
    }

    private static boolean passesChecks(
            final PassInputs inputs,
            final Segment segment,
            final String candidate,
            final GateResult.Restored restored) {
        final QaResult qa = QaEvaluation.evaluate(
                List.of(),
                segment,
                segment.masked(),
                candidate,
                restored.maskedForm(),
                inputs.frame(),
                inputs.namePolicy(),
                inputs.glossary().stream().map(GlossaryEntry::term).toList(),
                ProtectedSpans.mask(segment, inputs.frame(), inputs.glossary()).presentLocked());
        final boolean passes = qa.hardGatesPass() && !qa.failedOutright();
        log.debug(
                "Revision checked segmentId={} hardGatesPass={} failedOutright={} findings={} kept={}",
                segment.id(),
                qa.hardGatesPass(),
                qa.failedOutright(),
                qa.findings().stream().map(QaFinding::kind).toList(),
                passes);
        return passes;
    }
}
