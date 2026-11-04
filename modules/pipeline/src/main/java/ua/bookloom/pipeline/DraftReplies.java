package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.PauseDecider;

/**
 * How the draft step reads a reply, by design D3's precedence: a call's error, a blank or cut reply, a reply in the
 * wrong shape (one format re-ask), a reply that copies a shown example (one re-ask, then flagged), and then the
 * placeholder gate with the code repairs of what a reply lost.
 */
@Slf4j
final class DraftReplies {

    /** The error of a segment whose reply copied a shown example twice. */
    static final String EXAMPLE_COPIED = "Copied an example";

    /** The step key prefix of the re-ask of a reply that copied an example. */
    static final String ECHO_KEY = "echo:";

    /** The code repairs of a refused reply, in their order: put back what it lost, then place every token again. */
    private static final List<PlaceholderRepair> CODE_REPAIRS =
            List.of(PlaceholderRepair.RESTORE_MISSING, PlaceholderRepair.REWRAP_ALL);

    /** Which asks a draft has used: the one format re-ask, and the one re-ask of an item that copied an example. */
    record Sent(boolean reasked, boolean echoAsked) {
        static final Sent FIRST = new Sent(false, false);
    }

    /** Sends one step of a draft again, as the draft step sends its first. */
    @FunctionalInterface
    interface Sender {
        Result<DraftOutcome> send(DraftAttempt attempt, DraftStep step, String rejected, String diagnostic, Sent sent);
    }

    private final GateFunction gate;
    private final BookFormat format;
    private final DraftPrompts prompts;
    private final ModelCalls calls;
    private final Sender sender;

    DraftReplies(
            final GateFunction gate,
            final BookFormat format,
            final DraftPrompts prompts,
            final ModelCalls calls,
            final Sender sender) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.format = Objects.requireNonNull(format, "format");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    // Design D3 rule 1: an error the routing table flags at once is the segment's, every other one is the run's.
    Result<DraftOutcome> decideModelError(final DraftAttempt attempt, final AppError error) {
        final Segment segment = attempt.segment();
        log.debug("Model reply segment={} kind=error code={}", segment.id(), error.code());
        return switch (PauseDecider.route(error)) {
            case FLAG_AT_ONCE -> DraftOutcomes.flaggedAtOnce(attempt, error, "model-error", "[]");
            case CANCELLED, PAUSE_OR_FAIL, FAIL -> DraftOutcomes.routed(segment, error, "model-error");
        };
    }

    // Design D3 rules 2-4, in their order: blank content, then an abnormal finish, then a reply that is not the JSON
    // object — so a cut-off reply is flagged at once rather than sent to a format re-ask.
    Result<DraftOutcome> decideResponse(final DraftAttempt attempt, final ChatResponse response, final Sent sent) {
        if (response.content().isBlank() || response.finishReason() != FinishReason.STOP) {
            return DraftOutcomes.unfinished(attempt, response);
        }
        final ParsedReply parsed = prompts.parse(response.content(), attempt);
        if (parsed.kind() == ReplyKind.INVALID_STRUCTURED) {
            return sent.reasked()
                    ? DraftOutcomes.invalidStructuredReply(attempt, response)
                    : reask(attempt, response.content(), parsed.diagnostic(), sent);
        }
        final String trimmed = parsed.translation().strip();
        DraftOutcomes.traceReply(response.content(), trimmed);
        log.debug(
                "Model reply segment={} kind={} finish={} empty={} replyLength={}",
                attempt.segment().id(),
                parsed.kind(),
                response.finishReason(),
                trimmed.isEmpty(),
                trimmed.length());
        if (trimmed.isEmpty()) {
            return DraftOutcomes.unfinished(attempt, response);
        }
        return prompts.copiesExample(trimmed, attempt) ? echoed(attempt, response, sent) : restore(attempt, trimmed);
    }

    // The format re-ask fixes the envelope, not the translation, so it never uses up the segment's one repair.
    private Result<DraftOutcome> reask(
            final DraftAttempt attempt, final String rejectedReply, final String diagnostic, final Sent sent) {
        log.warn(
                "Re-asking an unreadable model reply segmentId={}",
                attempt.segment().id());
        return sender.send(attempt, DraftStep.STRUCTURAL_REPAIR, rejectedReply, diagnostic, sent);
    }

    // An item that copies a shown example is asked once more with another seed; a second copy flags the segment.
    private Result<DraftOutcome> echoed(final DraftAttempt attempt, final ChatResponse response, final Sent sent) {
        final String segmentId = attempt.segment().id();
        final String key = ECHO_KEY + segmentId;
        if (sent.echoAsked()) {
            log.warn("Model reply copied an example again segmentId={}; flagged", segmentId);
            calls.steps().ended(StepKind.RE_ASK, key, StepResult.FAILED);
            return DraftOutcomes.flaggedAtOnce(
                    attempt,
                    AppError.of(
                            ErrorCode.validation,
                            EXAMPLE_COPIED,
                            "The model copied a shown example instead of translating this segment."),
                    response.finishReason().name(),
                    DraftOutcomes.observedTokens(response.content()));
        }
        log.warn("Model reply copied an example segmentId={}; asking once more", segmentId);
        calls.steps().added(StepKind.RE_ASK, "re-ask", key, segmentId);
        return echoReask(attempt, sent.reasked());
    }

    // The one re-ask of an answer that copied an example, with the retry seed so the model does not repeat it.
    Result<DraftOutcome> echoReask(final DraftAttempt attempt, final boolean reasked) {
        final Result<DraftOutcome> again = sender.send(attempt, DraftStep.DRAFT, "", "", new Sent(reasked, true));
        if (again.data() instanceof DraftOutcome.Drafted) {
            calls.steps().ended(StepKind.RE_ASK, ECHO_KEY + attempt.segment().id(), StepResult.DONE);
        }
        return again;
    }

    Result<DraftOutcome> restore(final DraftAttempt attempt, final String trimmed) {
        final Segment segment = attempt.segment();
        log.debug("Restoring segment id={} format={} trimmedLength={}", segment.id(), format, trimmed.length());
        final String restoredWhitespace = WhitespaceRestoration.restore(segment.masked(), trimmed);
        logTraceRestoration(restoredWhitespace);
        return switch (gate.restore(segment, restoredWhitespace)) {
            case GateResult.Restored restored -> DraftOutcomes.drafted(attempt, restoredWhitespace, restored);
            case GateResult.GateFailed failed -> recover(attempt, restoredWhitespace, failed);
            case GateResult.StepError(var error) -> {
                log.debug("Gate completed segmentId={} result=StepError code={}", segment.id(), error.code());
                yield DraftOutcomes.routed(segment, error, "unmask");
            }
        };
    }

    // A reply the placeholder gate refused gets the code repairs before any model call: what it lost is put back, then
    // every token is placed again by position; a reply neither saves goes to the quality loop's one repair.
    private Result<DraftOutcome> recover(
            final DraftAttempt attempt, final String rejected, final GateResult.GateFailed failed) {
        final Segment segment = attempt.segment();
        log.debug(
                "Gate completed segmentId={} result=GateFailed code={}",
                segment.id(),
                failed.error().code());
        for (final PlaceholderRepair mode : CODE_REPAIRS) {
            if (gate.restoreRepairing(segment, rejected, mode) instanceof GateResult.Restored restored) {
                log.debug("Lost marks put back by code segmentId={} mode={}", segment.id(), mode);
                return DraftOutcomes.drafted(attempt, rejected, restored);
            }
        }
        return DraftOutcomes.stillFailingTheGate(attempt, rejected, failed);
    }

    private static void logTraceRestoration(final String restored) {
        if (log.isTraceEnabled()) {
            log.trace("Segment reply restored={}", restored);
        }
    }
}
