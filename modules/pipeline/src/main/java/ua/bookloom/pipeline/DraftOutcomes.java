package ua.bookloom.pipeline;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;

/**
 * The outcomes the draft step answers with, each written with its one log line, so {@link SegmentTranslator} holds
 * only the order in which a reply is read.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class DraftOutcomes {

    static Result<DraftOutcome> drafted(
            final DraftAttempt attempt, final String maskedReply, final GateResult.Restored restored) {
        final Segment segment = attempt.segment();
        log.debug(
                "Gate completed segmentId={} result=Restored targetLength={}",
                segment.id(),
                restored.restored().length());
        logTraceUnmask(restored.maskedForm(), restored.restored());
        log.debug(
                "Draft outcome id={} outcome=DRAFTED restored=true autoRepaired={}",
                segment.id(),
                restored.autoRepair() != null);
        return Result.ok(new DraftOutcome.Drafted(
                segment,
                attempt.shownText(),
                attempt.lockedRenderings(),
                maskedReply,
                restored.maskedForm(),
                restored.restored(),
                null,
                attempt.pieceRedraft(),
                restored.autoRepair(),
                null,
                restored.normalised()));
    }

    // Design D3 rule 5: a reply whose markup still does not restore after its repair is not flagged here; its markup
    // finding sends it to a directed fix that states the expected tokens.
    static Result<DraftOutcome> stillFailingTheGate(
            final DraftAttempt attempt, final String maskedReply, final GateResult.GateFailed failed) {
        final Segment segment = attempt.segment();
        log.warn(
                "Placeholder gate still fails after its repair segmentId={} expectedTokens={} observedTokens={}",
                segment.id(),
                expectedTokens(attempt),
                observedTokens(maskedReply));
        log.debug("Draft outcome id={} outcome=DRAFTED restored=false", segment.id());
        return Result.ok(new DraftOutcome.Drafted(
                segment,
                attempt.shownText(),
                attempt.lockedRenderings(),
                maskedReply,
                null,
                null,
                failed.finding(),
                attempt.pieceRedraft(),
                null,
                failed.candidate()));
    }

    // Design D3 rules 2-4: a reply with nothing self-heal could work on flags its segment at once.
    static Result<DraftOutcome> flaggedAtOnce(
            final DraftAttempt attempt, final AppError error, final String finish, final String observedTokens) {
        final Segment segment = attempt.segment();
        log.warn(
                "Flagged segment id={} code={} expectedTokens={} observedTokens={}",
                segment.id(),
                error.code(),
                expectedTokens(attempt),
                observedTokens);
        log.debug(
                "Draft outcome id={} outcome=FLAGGED_AT_ONCE replyFinish={} errorCode={}",
                segment.id(),
                finish,
                error.code());
        return Result.ok(
                new DraftOutcome.FlaggedAtOnce(segment, attempt.shownText(), attempt.lockedRenderings(), error));
    }

    static Result<DraftOutcome> routed(final Segment segment, final AppError error, final String replyKind) {
        log.debug(
                "Draft outcome id={} replyKind={} outcome=routed errorCode={}", segment.id(), replyKind, error.code());
        return Result.err(error);
    }

    private static String expectedTokens(final DraftAttempt attempt) {
        final String segmentId = attempt.segment().id();
        final int placeholderCount = Tokens.inOrder(attempt.shownText()).size();
        log.debug("Collected expected tokens segmentId={} placeholderCount={}", segmentId, placeholderCount);
        return DraftPromptBuilder.expectedTokenSequence(attempt.shownText());
    }

    static String observedTokens(final String text) {
        final List<String> tokens = Tokens.inOrder(text);
        log.debug("Collected observed tokens textLength={} tokenCount={}", text.length(), tokens.size());
        return tokens.toString();
    }

    static AppError emptyCompletion() {
        log.debug("Creating segment error code={} reason=empty-reply", ErrorCode.emptyCompletion);
        return AppError.of(ErrorCode.emptyCompletion, "Empty model response", "The model returned no translated text.");
    }

    static AppError invalidFinish() {
        log.debug("Creating segment error code={} reason=non-stop-finish", ErrorCode.validation);
        return AppError.of(
                ErrorCode.validation, "Incomplete model response", "The model response did not finish normally.");
    }

    // Design D3 rules 2-3: a blank reply, or one that did not finish normally, is flagged at once.
    static Result<DraftOutcome> unfinished(final DraftAttempt attempt, final ChatResponse response) {
        final boolean empty = response.content().isBlank() || response.finishReason() == FinishReason.STOP;
        return flaggedAtOnce(
                attempt,
                empty ? emptyCompletion() : invalidFinish(),
                response.finishReason().name(),
                observedTokens(response.content()));
    }

    // Design D3 rule 4: a reply that is still not the JSON object after its structural repair is flagged at once.
    static Result<DraftOutcome> invalidStructuredReply(final DraftAttempt attempt, final ChatResponse response) {
        traceReply(response.content(), "");
        return flaggedAtOnce(
                attempt,
                AppError.of(
                        ErrorCode.validation,
                        "Invalid structured model response",
                        "The model did not return the required translation JSON object."),
                response.finishReason().name(),
                observedTokens(response.content()));
    }

    static void traceReply(final String raw, final String trimmed) {
        if (log.isTraceEnabled()) {
            log.trace("Segment reply raw={} trimmed={}", raw, trimmed);
        }
    }

    private static void logTraceUnmask(final String input, final String output) {
        if (log.isTraceEnabled()) {
            log.trace("Segment unmask input={} output={}", input, output);
        }
    }
}
