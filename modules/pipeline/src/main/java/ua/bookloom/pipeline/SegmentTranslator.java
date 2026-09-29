package ua.bookloom.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.chunk.OversizedSplit;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.run.PauseDecider;

/**
 * The draft step: one draft call, with at most one structural and one placeholder repair, whose reply is read by
 * design D3's reply precedence. It decides nothing — a reply it can use goes to the quality loop, which accepts,
 * repairs or flags it — so the chunk runner and a review retry share one draft step.
 */
@Slf4j
public final class SegmentTranslator {

    private final GateFunction gate;
    private final ModelCalls calls;
    private final BookFormat format;
    private final DraftPromptBuilder promptBuilder;
    private final DraftReplyParser replyParser;

    /**
     * Creates the draft step of one run.
     *
     * @param gate the non-null placeholder gate every reply is restored through
     * @param calls the non-null seam every call of the step goes through
     * @param format the non-null book format
     * @param promptBuilder the non-null builder of the run's draft and repair prompts
     * @param replyParser the non-null strict reader of a draft reply
     */
    public SegmentTranslator(
            final GateFunction gate,
            final ModelCalls calls,
            final BookFormat format,
            final DraftPromptBuilder promptBuilder,
            final DraftReplyParser replyParser) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.format = Objects.requireNonNull(format, "format");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.replyParser = Objects.requireNonNull(replyParser, "replyParser");
    }

    Result<DraftOutcome> translate(final Segment segment) {
        return translate(segment, DraftContext.empty());
    }

    /**
     * Drafts one segment.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null context the draft is shown, preceding targets included
     * @return the draft's outcome, or the error a call answered, which the run routes
     */
    public Result<DraftOutcome> translate(final Segment segment, final DraftContext context) {
        Objects.requireNonNull(segment, "segment");
        return translate(segment, context, segment.masked());
    }

    /**
     * Drafts one segment showing the model {@code shownText} — the segment's masked text with its protected spans
     * hidden behind tokens — so the prompt, its repairs and the output allowance all follow that text.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null context the draft is shown
     * @param shownText the non-null text the model translates; it carries every token the reply must return
     * @return the draft's outcome, or the error a call answered, which the run routes
     */
    public Result<DraftOutcome> translate(final Segment segment, final DraftContext context, final String shownText) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shownText, "shownText");
        final DraftAttempt attempt = new DraftAttempt(segment, context, shownText);
        log.debug("Translating segment id={} format={} shownLength={}", segment.id(), format, shownText.length());
        final ChatRequest request = requestFor(attempt, DraftStep.DRAFT, "", "");
        final Result<ChatResponse> reply = callModel(DraftStep.DRAFT, segment, request);
        if (reply.isErr()) {
            return decideModelError(attempt, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(attempt, Objects.requireNonNull(reply.data()), false, false);
    }

    /**
     * Drafts a segment, in sentence-aligned pieces when its masked text alone is above the budget. The pieces are
     * joined and gated once as that one segment, because the translation goes back into the node it came from.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null context each piece is shown
     * @param splitter the non-null sentence splitter of the source language
     * @param budgetTokens the chunk budget a piece must fit
     * @return the draft's outcome, or the error a call answered, which the run routes
     */
    public Result<DraftOutcome> translateSplit(
            final Segment segment,
            final DraftContext context,
            final SentenceSplitter splitter,
            final int budgetTokens) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(splitter, "splitter");
        final int estimate = TokenEstimator.estimate(segment.masked(), promptBuilder.sourceLanguage());
        if (estimate <= budgetTokens) {
            log.debug(
                    "Segment id={} estimate={} within budget={}, drafted whole", segment.id(), estimate, budgetTokens);
            return translate(segment, context);
        }
        return switch (OversizedSplit.plan(segment, promptBuilder.sourceLanguage(), budgetTokens, splitter)) {
            case OversizedSplit.Pieces plan -> translatePieces(segment, context, plan.pieces());
            case OversizedSplit.Unsplittable none -> {
                log.warn(
                        "Oversized segment {} cannot be split estimate={} budget={}; drafting it whole without"
                                + " preceding targets",
                        segment.id(),
                        estimate,
                        budgetTokens);
                yield translate(segment, DraftContext.empty());
            }
        };
    }

    /** Drafts each piece with the segment's own context, joins the replies in order and gates them once. */
    Result<DraftOutcome> translatePieces(final Segment segment, final DraftContext context, final List<String> pieces) {
        log.debug("Translating segment id={} in {} pieces", segment.id(), pieces.size());
        final PieceDrafter drafter = new PieceDrafter(this, replyParser);
        final DraftAttempt whole = DraftAttempt.showingItsOwnMaskedText(segment, context);
        final List<String> replies = new ArrayList<>();
        for (final String piece : pieces) {
            final Result<PieceDrafter.Piece> drafted = drafter.draft(PieceDrafter.pieceOf(segment, piece), context);
            if (drafted.isErr()) {
                return decideModelError(whole, Objects.requireNonNull(drafted.error()));
            }
            final PieceDrafter.Piece drafts = Objects.requireNonNull(drafted.data());
            if (drafts instanceof PieceDrafter.Unusable(final AppError unusable)) {
                return DraftOutcomes.flaggedAtOnce(whole, unusable, FinishReason.STOP.name(), "[]");
            }
            if (drafts instanceof PieceDrafter.Text(final String text)) {
                replies.add(WhitespaceRestoration.restore(piece, text));
            }
        }
        return restore(whole, String.join("", replies).strip(), true);
    }

    ChatRequest requestFor(
            final Segment segment,
            final DraftContext context,
            final DraftStep step,
            final String rejected,
            final String diagnostic) {
        return requestFor(DraftAttempt.showingItsOwnMaskedText(segment, context), step, rejected, diagnostic);
    }

    private ChatRequest requestFor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final OutputLimit limit = OutputLimit.forSource(
                attempt.shownText(), promptBuilder.sourceLanguage(), promptBuilder.targetLanguage());
        final ChatRequest request =
                ChatRequests.build(step.promptName(), messagesFor(attempt, step, rejected, diagnostic), limit, false);
        log.debug(
                "Built chat request segmentId={} maskedLength={} messageCount={} expectedTokens={} capTokens={}",
                attempt.segment().id(),
                attempt.shownText().length(),
                request.messages().size(),
                request.expectedOutputTokens() == null ? "none" : request.expectedOutputTokens(),
                request.maxOutputTokens() == null ? "none" : request.maxOutputTokens());
        return request;
    }

    private List<ua.bookloom.api.llm.ChatMessage> messagesFor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final Segment segment = attempt.segment();
        final DraftContext context = attempt.context();
        final String shown = attempt.shownText();
        return switch (step) {
            case DRAFT -> promptBuilder.messagesFor(segment, context, shown);
            case STRUCTURAL_REPAIR ->
                promptBuilder.messagesForStructuredRepair(segment, context, shown, rejected, diagnostic);
            case PLACEHOLDER_REPAIR ->
                promptBuilder.messagesForPlaceholderRepair(
                        segment, context, shown, rejected, diagnostic.isEmpty() ? null : diagnostic);
        };
    }

    Result<ChatResponse> callModel(final DraftStep step, final Segment segment, final ChatRequest request) {
        log.debug(
                "Calling chat model segmentId={} messageCount={}",
                segment.id(),
                request.messages().size());
        try {
            final Result<ChatResponse> result =
                    Objects.requireNonNull(calls.call(step.callKind(), segment.id(), request), "model result");
            log.debug("Chat model completed segmentId={} result={}", segment.id(), result.isOk() ? "success" : "error");
            return result;
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Translation failed",
                    "The model could not translate this segment.",
                    null,
                    cause);
            log.debug("Chat model completed segmentId={} result=thrown errorCode={}", segment.id(), error.code());
            log.error("Unexpected model failure segment={} code={}", segment.id(), error.code(), cause);
            return Result.err(error);
        }
    }

    // Design D3 rule 1: an error the routing table flags at once is the segment's, every other one is the run's.
    private Result<DraftOutcome> decideModelError(final DraftAttempt attempt, final AppError error) {
        final Segment segment = attempt.segment();
        log.debug("Model reply segment={} kind=error code={}", segment.id(), error.code());
        return switch (PauseDecider.route(error.code())) {
            case FLAG_AT_ONCE -> DraftOutcomes.flaggedAtOnce(attempt, error, "model-error", "[]");
            case CANCELLED, PAUSE_OR_FAIL, FAIL -> DraftOutcomes.routed(segment, error, "model-error");
        };
    }

    // Design D3 rules 2-4, in their order: blank content, then an abnormal finish, then a reply that is not the JSON
    // object — so a cut-off reply is flagged at once rather than sent to a structural repair.
    private Result<DraftOutcome> decideResponse(
            final DraftAttempt attempt,
            final ChatResponse response,
            final boolean structuralRepairUsed,
            final boolean placeholderRepairUsed) {
        if (response.content().isBlank() || response.finishReason() != FinishReason.STOP) {
            return unfinished(attempt, response);
        }
        final ParsedReply parsed = replyParser.parse(response.content());
        if (parsed.kind() == ReplyKind.INVALID_STRUCTURED) {
            return structuralRepairUsed
                    ? invalidStructuredReply(attempt, response)
                    : repairStructured(attempt, response.content(), parsed.diagnostic());
        }
        final String trimmed = parsed.translation().strip();
        logTraceReply(response.content(), trimmed);
        log.debug(
                "Model reply segment={} kind={} finish={} empty={}",
                attempt.segment().id(),
                parsed.kind(),
                response.finishReason(),
                trimmed.isEmpty());
        return trimmed.isEmpty() ? unfinished(attempt, response) : restore(attempt, trimmed, placeholderRepairUsed);
    }

    private static Result<DraftOutcome> unfinished(final DraftAttempt attempt, final ChatResponse response) {
        final boolean empty = response.content().isBlank() || response.finishReason() == FinishReason.STOP;
        return DraftOutcomes.flaggedAtOnce(
                attempt,
                empty ? DraftOutcomes.emptyCompletion() : DraftOutcomes.invalidFinish(),
                response.finishReason().name(),
                DraftOutcomes.observedTokens(response.content()));
    }

    private Result<DraftOutcome> repairStructured(
            final DraftAttempt attempt, final String rejectedReply, final String diagnostic) {
        final Segment segment = attempt.segment();
        log.warn("Repairing invalid structured model reply segmentId={}", segment.id());
        final ChatRequest request = requestFor(attempt, DraftStep.STRUCTURAL_REPAIR, rejectedReply, diagnostic);
        final Result<ChatResponse> reply = callModel(DraftStep.STRUCTURAL_REPAIR, segment, request);
        if (reply.isErr()) {
            return decideModelError(attempt, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(attempt, Objects.requireNonNull(reply.data()), true, false);
    }

    private Result<DraftOutcome> invalidStructuredReply(final DraftAttempt attempt, final ChatResponse response) {
        logTraceReply(response.content(), "");
        return DraftOutcomes.flaggedAtOnce(
                attempt,
                AppError.of(
                        ErrorCode.validation,
                        "Invalid structured model response",
                        "The model did not return the required translation JSON object."),
                response.finishReason().name(),
                DraftOutcomes.observedTokens(response.content()));
    }

    private Result<DraftOutcome> restore(
            final DraftAttempt attempt, final String trimmed, final boolean placeholderRepairUsed) {
        final Segment segment = attempt.segment();
        log.debug("Restoring segment id={} format={} trimmedLength={}", segment.id(), format, trimmed.length());
        final String restoredWhitespace = WhitespaceRestoration.restore(segment.masked(), trimmed);
        logTraceRestoration(restoredWhitespace);
        return switch (gate.restore(segment, restoredWhitespace)) {
            case GateResult.Restored restored -> DraftOutcomes.drafted(attempt, restoredWhitespace, restored);
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Gate completed segmentId={} result=GateFailed code={}",
                        segment.id(),
                        failed.error().code());
                yield placeholderRepairUsed
                        ? DraftOutcomes.stillFailingTheGate(attempt, restoredWhitespace, failed)
                        : repairPlaceholder(
                                attempt, restoredWhitespace, failed.finding().note());
            }
            case GateResult.StepError stepError -> {
                log.debug(
                        "Gate completed segmentId={} result=StepError code={}",
                        segment.id(),
                        stepError.error().code());
                yield DraftOutcomes.routed(segment, stepError.error(), "unmask");
            }
        };
    }

    private Result<DraftOutcome> repairPlaceholder(
            final DraftAttempt attempt, final String rejectedTarget, final String gateNote) {
        final Segment segment = attempt.segment();
        log.warn("Repairing placeholder mismatch segmentId={}", segment.id());
        final ChatRequest request = requestFor(attempt, DraftStep.PLACEHOLDER_REPAIR, rejectedTarget, gateNote);
        final Result<ChatResponse> reply = callModel(DraftStep.PLACEHOLDER_REPAIR, segment, request);
        if (reply.isErr()) {
            return decideModelError(attempt, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(attempt, Objects.requireNonNull(reply.data()), true, true);
    }

    private static void logTraceReply(final String raw, final String trimmed) {
        if (log.isTraceEnabled()) {
            log.trace("Segment reply raw={} trimmed={}", raw, trimmed);
        }
    }

    private static void logTraceRestoration(final String restored) {
        if (log.isTraceEnabled()) {
            log.trace("Segment reply restored={}", restored);
        }
    }
}
