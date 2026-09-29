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
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.chunk.OversizedSplit;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.DraftStep;

/** Makes one model call and decides one segment. */
@Slf4j
final class SegmentTranslator {

    private final GateFunction gate;
    private final ChatModel model;
    private final BookFormat format;
    private final DraftPromptBuilder promptBuilder;
    private final DraftReplyParser replyParser;

    SegmentTranslator(
            final GateFunction gate,
            final ChatModel model,
            final BookFormat format,
            final DraftPromptBuilder promptBuilder,
            final DraftReplyParser replyParser) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.model = Objects.requireNonNull(model, "model");
        this.format = Objects.requireNonNull(format, "format");
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
        this.replyParser = Objects.requireNonNull(replyParser, "replyParser");
    }

    Result<Decision> translate(final Segment segment) {
        return translate(segment, DraftContext.empty());
    }

    /** Translates one segment with previously accepted targets supplied solely as context. */
    Result<Decision> translate(final Segment segment, final DraftContext context) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        log.debug("Translating segment id={} format={}", segment.id(), format);
        final ChatRequest request = requestFor(segment, context, DraftStep.DRAFT, "", "");
        final Result<ChatResponse> reply = callModel(segment, request);
        if (reply.isErr()) {
            return decideModelError(segment, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(segment, context, Objects.requireNonNull(reply.data()), false, false);
    }

    /**
     * Translates a segment, in sentence-aligned pieces when its masked text alone is above the budget. The pieces are
     * joined and gated once as that one segment, because the translation goes back into the node it came from.
     */
    Result<Decision> translateSplit(
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

    /** Drafts each piece with the segment's own context, joins the replies in order and decides once. */
    Result<Decision> translatePieces(final Segment segment, final DraftContext context, final List<String> pieces) {
        log.debug("Translating segment id={} in {} pieces", segment.id(), pieces.size());
        final PieceDrafter drafter = new PieceDrafter(this, replyParser);
        final List<String> replies = new ArrayList<>();
        for (final String piece : pieces) {
            final Result<String> drafted = drafter.draft(PieceDrafter.pieceOf(segment, piece), context);
            if (drafted.isErr()) {
                return decideModelError(segment, Objects.requireNonNull(drafted.error()));
            }
            replies.add(WhitespaceRestoration.restore(piece, Objects.requireNonNull(drafted.data())));
        }
        return restore(segment, context, String.join("", replies).strip(), true);
    }

    ChatRequest requestFor(
            final Segment segment,
            final DraftContext context,
            final DraftStep step,
            final String rejected,
            final String diagnostic) {
        log.debug(
                "Building chat request segmentId={} maskedLength={}",
                segment.id(),
                segment.masked().length());
        final int allowance = TokenEstimator.outputAllowance(
                DisplayText.of(segment.masked()), promptBuilder.sourceLanguage(), promptBuilder.targetLanguage());
        final ChatRequest request = ChatRequests.build(
                step.promptName(),
                messagesFor(segment, context, step, rejected, diagnostic),
                allowance > 0 ? allowance : null,
                false);
        log.debug(
                "Built chat request segmentId={} messageCount={}",
                segment.id(),
                request.messages().size());
        return request;
    }

    private List<ua.bookloom.api.llm.ChatMessage> messagesFor(
            final Segment segment,
            final DraftContext context,
            final DraftStep step,
            final String rejected,
            final String diagnostic) {
        return switch (step) {
            case DRAFT -> promptBuilder.messagesFor(segment, context);
            case STRUCTURAL_REPAIR -> promptBuilder.messagesForStructuredRepair(segment, context, rejected, diagnostic);
            case PLACEHOLDER_REPAIR ->
                promptBuilder.messagesForPlaceholderRepair(
                        segment, context, rejected, diagnostic.isEmpty() ? null : diagnostic);
        };
    }

    Result<ChatResponse> callModel(final Segment segment, final ChatRequest request) {
        log.debug(
                "Calling chat model segmentId={} messageCount={}",
                segment.id(),
                request.messages().size());
        try {
            final Result<ChatResponse> result = Objects.requireNonNull(model.chat(request), "model result");
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

    private Result<Decision> decideModelError(final Segment segment, final AppError error) {
        log.debug("Model reply segment={} kind=error code={}", segment.id(), error.code());
        return switch (error.code()) {
            case validation, emptyCompletion, contextWindow -> flag(segment, error, "model-error", "[]");
            default -> terminal(segment, error, "model-error");
        };
    }

    private Result<Decision> decideResponse(
            final Segment segment,
            final DraftContext context,
            final ChatResponse response,
            final boolean structuralRepairUsed,
            final boolean placeholderRepairUsed) {
        if (response.content().isBlank()) {
            return flag(segment, emptyCompletion(), response.finishReason().name(), observedTokens(response.content()));
        }
        final ParsedReply parsed = replyParser.parse(response.content());
        if (parsed.kind() == ReplyKind.INVALID_STRUCTURED) {
            return structuralRepairUsed
                    ? invalidStructuredReply(segment, response)
                    : repairStructured(segment, context, response.content(), parsed.diagnostic());
        }
        final String trimmed = parsed.translation().strip();
        logTraceReply(response.content(), trimmed);
        log.debug(
                "Model reply segment={} kind={} finish={} empty={}",
                segment.id(),
                parsed.kind(),
                response.finishReason(),
                trimmed.isEmpty());
        if (trimmed.isEmpty()) {
            return flag(segment, emptyCompletion(), response.finishReason().name(), observedTokens(response.content()));
        }
        if (response.finishReason() != FinishReason.STOP) {
            return flag(segment, invalidFinish(), response.finishReason().name(), observedTokens(response.content()));
        }
        return restore(segment, context, trimmed, placeholderRepairUsed);
    }

    private Result<Decision> repairStructured(
            final Segment segment, final DraftContext context, final String rejectedReply, final String diagnostic) {
        log.warn("Repairing invalid structured model reply segmentId={}", segment.id());
        final ChatRequest request =
                requestFor(segment, context, DraftStep.STRUCTURAL_REPAIR, rejectedReply, diagnostic);
        final Result<ChatResponse> reply = callModel(segment, request);
        if (reply.isErr()) {
            return decideModelError(segment, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(segment, context, Objects.requireNonNull(reply.data()), true, false);
    }

    private Result<Decision> invalidStructuredReply(final Segment segment, final ChatResponse response) {
        logTraceReply(response.content(), "");
        return flag(
                segment,
                AppError.of(
                        ErrorCode.validation,
                        "Invalid structured model response",
                        "The model did not return the required translation JSON object."),
                response.finishReason().name(),
                observedTokens(response.content()));
    }

    private Result<Decision> restore(
            final Segment segment,
            final DraftContext context,
            final String trimmed,
            final boolean placeholderRepairUsed) {
        log.debug("Restoring segment id={} format={} trimmedLength={}", segment.id(), format, trimmed.length());
        final String restoredWhitespace = WhitespaceRestoration.restore(segment.masked(), trimmed);
        logTraceRestoration(restoredWhitespace);
        return switch (gate.restore(segment, restoredWhitespace)) {
            case GateResult.Restored restored -> accepted(segment, restored);
            case GateResult.GateFailed failed -> {
                log.debug(
                        "Gate completed segmentId={} result=GateFailed code={}",
                        segment.id(),
                        failed.error().code());
                yield placeholderRepairUsed
                        ? flag(segment, failed.error(), FinishReason.STOP.name(), observedTokens(restoredWhitespace))
                        : repairPlaceholder(
                                segment,
                                context,
                                restoredWhitespace,
                                failed.finding().note());
            }
            case GateResult.StepError stepError -> {
                log.debug(
                        "Gate completed segmentId={} result=StepError code={}",
                        segment.id(),
                        stepError.error().code());
                yield terminal(segment, stepError.error(), "unmask");
            }
        };
    }

    private static Result<Decision> accepted(final Segment segment, final GateResult.Restored restored) {
        log.debug(
                "Gate completed segmentId={} result=Restored targetLength={}",
                segment.id(),
                restored.restored().length());
        logTraceUnmask(restored.maskedForm(), restored.restored());
        final Decision decision = new Decision(segment.withDecision(SegmentStatus.ACCEPTED, restored.restored()), null);
        log.debug("Segment decision id={} decision={} errorCode={}", segment.id(), SegmentStatus.ACCEPTED, null);
        return Result.ok(decision);
    }

    private Result<Decision> repairPlaceholder(
            final Segment segment, final DraftContext context, final String rejectedTarget, final String gateNote) {
        log.warn("Repairing placeholder mismatch segmentId={}", segment.id());
        final ChatRequest request =
                requestFor(segment, context, DraftStep.PLACEHOLDER_REPAIR, rejectedTarget, gateNote);
        final Result<ChatResponse> reply = callModel(segment, request);
        if (reply.isErr()) {
            return decideModelError(segment, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(segment, context, Objects.requireNonNull(reply.data()), true, true);
    }

    private Result<Decision> flag(
            final Segment segment, final AppError error, final String finish, final String observedTokens) {
        final String expectedTokens = expectedTokens(segment);
        log.warn(
                "Flagged segment id={} code={} expectedTokens={} observedTokens={}",
                segment.id(),
                error.code(),
                expectedTokens,
                observedTokens);
        log.debug(
                "Segment decision id={} replyFinish={} decision={} errorCode={}",
                segment.id(),
                finish,
                SegmentStatus.FLAGGED,
                error.code());
        return Result.ok(new Decision(segment.withDecision(SegmentStatus.FLAGGED, null), error));
    }

    private static Result<Decision> terminal(final Segment segment, final AppError error, final String replyKind) {
        log.debug(
                "Segment decision id={} replyKind={} decision=terminal errorCode={}",
                segment.id(),
                replyKind,
                error.code());
        return Result.err(error);
    }

    private static String expectedTokens(final Segment segment) {
        log.debug(
                "Collecting expected tokens segmentId={} placeholderCount={}",
                segment.id(),
                segment.placeholders().size());
        final String tokens = DraftPromptBuilder.expectedTokenSequence(segment);
        log.debug(
                "Collected expected tokens segmentId={} tokenCount={}",
                segment.id(),
                segment.placeholders().size());
        return tokens;
    }

    private static String observedTokens(final String text) {
        log.debug("Collecting observed tokens textLength={}", text.length());
        final List<String> tokens = Tokens.inOrder(text);
        log.debug("Collected observed tokens textLength={} tokenCount={}", text.length(), tokens.size());
        return tokens.toString();
    }

    private static AppError emptyCompletion() {
        log.debug("Creating segment error code={} reason=empty-reply", ErrorCode.emptyCompletion);
        return AppError.of(ErrorCode.emptyCompletion, "Empty model response", "The model returned no translated text.");
    }

    private static AppError invalidFinish() {
        log.debug("Creating segment error code={} reason=non-stop-finish", ErrorCode.validation);
        return AppError.of(
                ErrorCode.validation, "Incomplete model response", "The model response did not finish normally.");
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

    private static void logTraceUnmask(final String input, final String output) {
        if (log.isTraceEnabled()) {
            log.trace("Segment unmask input={} output={}", input, output);
        }
    }
}
