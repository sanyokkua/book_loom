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
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.heal.RepairReplies;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.heal.SelfHealCalls;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

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
    private static final String UNREADABLE = "unreadable";
    private static final String GATE = "gate";

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
        final Map<String, String> user = new HashMap<>();
        user.put("source", segment.masked());
        user.put("text", maskedTarget);
        user.put("resolvedFacts", facts);
        user.put("tokens", SelfHealCalls.immutableTokens(segment.masked()));
        return send(PromptName.REVISION, inputs, segment, maskedTarget, user, RevisionGuards.Mode.SAME_COUNTS, calls)
                .map(RevisionAnswer::revised);
    }

    /**
     * Checks one paragraph against the paragraphs around it.
     *
     * @param inputs what the pass read as it started
     * @param segment the opened book's segment
     * @param maskedTarget the target to check, the document's own tokens in place
     * @param user the user template's slots, {@link NeighbourPrompt#slots} built from the same target
     * @param mode how the guards compare the counts: a flagged paragraph's fix may restore what its draft lost
     * @param calls the seam the call is sent through
     * @return the checked target restored through the gate, equal to the given one when nothing needed to change, or
     *     the reason the answer was refused; or the call's own error
     */
    Result<RevisionAnswer> checkAgainstNeighbours(
            final PassInputs inputs,
            final Segment segment,
            final String maskedTarget,
            final Map<String, String> user,
            final RevisionGuards.Mode mode,
            final ModelCalls calls) {
        return send(PromptName.CONSISTENCY, inputs, segment, maskedTarget, user, mode, calls);
    }

    private Result<RevisionAnswer> send(
            final PromptName name,
            final PassInputs inputs,
            final Segment segment,
            final String maskedTarget,
            final Map<String, String> user,
            final RevisionGuards.Mode mode,
            final ModelCalls calls) {
        final List<ChatMessage> messages = SelfHealCalls.messagesFor(templates, name, inputs.frame(), user);
        final ChatRequest request =
                ChatRequests.build(name, messages, SelfHealCalls.outputLimit(segment.masked(), inputs.frame()), false);
        SelfHealCalls.logTraceMessages(log, LABEL, request);
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.REVISION,
                List.of(segment.id()),
                request,
                SelfHealCalls.descriptor(templates, name, inputs.frame(), segment.masked(), user));
        SelfHealCalls.logTraceReply(log, LABEL, reply);
        final Result<RepairReply> read = RepairReplies.read(reply, replyParser, segment.masked());
        SelfHealCalls.logOutcome(log, LABEL, segment.id(), read);
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        return switch (Objects.requireNonNull(read.data(), "read")) {
            case RepairReply.Rewritten rewritten ->
                gated(inputs, segment, maskedTarget, rewritten.maskedTarget(), mode);
            case RepairReply.Malformed malformed -> Result.ok(new RevisionAnswer.Refused(UNREADABLE));
            case RepairReply.FlagNow flagNow -> Result.ok(new RevisionAnswer.Refused(UNREADABLE));
        };
    }

    // An answer equal to the old text needs no check: it changes nothing, even when the old text fails one.
    private static Result<RevisionAnswer> gated(
            final PassInputs inputs,
            final Segment segment,
            final String before,
            final String reply,
            final RevisionGuards.Mode mode) {
        final String candidate = WhitespaceRestoration.restore(segment.masked(), reply);
        return switch (inputs.gate().restore(segment, candidate)) {
            case GateResult.Restored restored -> {
                final Optional<String> refusal = restored.maskedForm().equals(before)
                        ? Optional.empty()
                        : PassChecks.refusal(inputs, segment, before, candidate, restored.maskedForm(), mode);
                yield Result.ok(refusal.<RevisionAnswer>map(RevisionAnswer.Refused::new)
                        .orElseGet(() -> new RevisionAnswer.Revised(restored)));
            }
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Revision refused segmentId={}: the gate failed raisedBy={}",
                        segment.id(),
                        failed.finding().raisedBy());
                yield Result.ok(new RevisionAnswer.Refused(GATE));
            }
            case GateResult.StepError stepError -> Result.err(stepError.error());
        };
    }
}
