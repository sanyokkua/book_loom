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
import ua.bookloom.pipeline.heal.RoundProgress;
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
        final Map<String, String> user = new HashMap<>();
        user.put("source", segment.masked());
        user.put("text", maskedTarget);
        user.put("resolvedFacts", facts);
        user.put("tokens", SelfHealCalls.immutableTokens(segment.masked()));
        return send(PromptName.REVISION, inputs, segment, maskedTarget, user, calls);
    }

    /**
     * Checks one paragraph against the translated paragraphs around it.
     *
     * @param inputs what the pass read as it started
     * @param segment the opened book's segment
     * @param maskedTarget the target to check, the document's own tokens in place
     * @param facts one line per name or term of the paragraph and the rendering the book holds for it; may be empty
     * @param previous the translated paragraph before it, or empty at the start of the book
     * @param next the translated paragraph after it, or empty at the end of the book
     * @param calls the seam the call is sent through
     * @return the checked target restored through the gate, equal to the given one when nothing needed to change;
     *     empty when the reply was unreadable or refused by a gate or a check; or the call's own error
     */
    Result<Optional<GateResult.Restored>> checkAgainstNeighbours(
            final PassInputs inputs,
            final Segment segment,
            final String maskedTarget,
            final String facts,
            final String previous,
            final String next,
            final ModelCalls calls) {
        final Map<String, String> user = new HashMap<>();
        user.put("source", segment.masked());
        user.put("text", maskedTarget);
        user.put("resolvedFacts", facts);
        user.put("previous", previous);
        user.put("next", next);
        user.put("tokens", SelfHealCalls.immutableTokens(segment.masked()));
        return send(PromptName.CONSISTENCY, inputs, segment, maskedTarget, user, calls);
    }

    private Result<Optional<GateResult.Restored>> send(
            final PromptName name,
            final PassInputs inputs,
            final Segment segment,
            final String maskedTarget,
            final Map<String, String> user,
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
        final Result<RepairReply> read = RepairReplies.read(reply, replyParser);
        SelfHealCalls.logOutcome(log, LABEL, segment.id(), read);
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        return switch (Objects.requireNonNull(read.data(), "read")) {
            case RepairReply.Rewritten rewritten -> gated(inputs, segment, maskedTarget, rewritten.maskedTarget());
            case RepairReply.Malformed malformed -> Result.ok(Optional.empty());
            case RepairReply.FlagNow flagNow -> Result.ok(Optional.empty());
        };
    }

    private static Result<Optional<GateResult.Restored>> gated(
            final PassInputs inputs, final Segment segment, final String before, final String reply) {
        final String candidate = WhitespaceRestoration.restore(segment.masked(), reply);
        return switch (inputs.gate().restore(segment, candidate)) {
            case GateResult.Restored restored ->
                Result.ok(
                        passesChecks(inputs, segment, before, candidate, restored)
                                ? Optional.of(restored)
                                : Optional.empty());
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

    // The new text is kept only when it is no worse than the old one by the same checks, keeps what the old one had
    // (quotes, dashes, sentences, words) and brings in no foreign run: a revision the checks cannot see is still a fix,
    // but one that loses or invents is a regression.
    private static boolean passesChecks(
            final PassInputs inputs,
            final Segment segment,
            final String before,
            final String candidate,
            final GateResult.Restored restored) {
        final QaResult qa = evaluate(inputs, segment, candidate, restored.maskedForm());
        final QaResult old = evaluate(inputs, segment, before, before);
        final boolean checked = qa.hardGatesPass() && !qa.failedOutright();
        final boolean worse = RoundProgress.isWorse(qa, old);
        final boolean preserves = RevisionGuards.preserves(before, restored.maskedForm());
        final boolean passes = checked && !worse && preserves;
        log.debug(
                "Revision checked segmentId={} hardGatesPass={} failedOutright={} worse={} preserves={} findings={}"
                        + " kept={}",
                segment.id(),
                qa.hardGatesPass(),
                qa.failedOutright(),
                worse,
                preserves,
                qa.findings().stream().map(QaFinding::kind).toList(),
                passes);
        return passes;
    }

    private static QaResult evaluate(
            final PassInputs inputs, final Segment segment, final String candidate, final String maskedForm) {
        return QaEvaluation.evaluate(
                List.of(),
                segment,
                segment.masked(),
                candidate,
                maskedForm,
                inputs.frame(),
                inputs.namePolicy(),
                inputs.glossary().stream().map(GlossaryEntry::term).toList(),
                ProtectedSpans.mask(segment, inputs.frame(), inputs.glossary()).presentLocked(),
                pairs(inputs.glossary()));
    }

    private static List<String> pairs(final List<GlossaryEntry> glossary) {
        return glossary.stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .map(entry -> entry.term() + " → " + entry.target())
                .toList();
    }
}
