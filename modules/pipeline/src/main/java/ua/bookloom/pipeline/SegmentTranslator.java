package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.PlaceholderRepair;
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
import ua.bookloom.pipeline.heal.PieceRedraft;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.GateNotes;
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

    /** Creates the draft step of one run over its placeholder gate, call seam, format, prompts and reply reader. */
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
        return translate(segment, DraftContext.empty(), ProtectedMask.none(segment.masked()));
    }

    /**
     * The same draft step restoring every reply through {@code chunkGate}, which knows the protected spans of one
     * chunk's segments.
     *
     * @param chunkGate the non-null gate of the chunk about to be drafted
     * @return a draft step sharing this one's seam, format, prompts and reply reader
     */
    public SegmentTranslator gatedBy(final GateFunction chunkGate) {
        return new SegmentTranslator(
                Objects.requireNonNull(chunkGate, "chunkGate"), calls, format, promptBuilder, replyParser);
    }

    /** The run's draft of one segment: no extra instruction, at the draft's own temperature. */
    public Result<DraftOutcome> translate(final Segment segment, final DraftContext context, final ProtectedMask mask) {
        return translate(segment, context, mask, "", false);
    }

    /**
     * Drafts one segment showing the model its mask's text — the segment's masked text with its protected spans
     * hidden behind tokens — so the prompt, its repairs and the output allowance all follow that text. A review retry
     * passes the person's note, which the draft and its repairs carry, and may ask the draft call alone for its lower
     * temperature.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null context the draft is shown
     * @param mask the non-null spans hidden in the segment; its text carries every token the reply must return
     * @param extraInstruction the non-null note shown under {@code [Extra instruction]}; empty for none
     * @param lowerTemperature whether the draft call asks for its lower temperature
     * @return the draft's outcome, or the error a call answered, which the run routes
     */
    public Result<DraftOutcome> translate(
            final Segment segment,
            final DraftContext context,
            final ProtectedMask mask,
            final String extraInstruction,
            final boolean lowerTemperature) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(extraInstruction, "extraInstruction");
        final DraftAttempt attempt = DraftAttempt.of(segment, context, mask, extraInstruction, lowerTemperature);
        log.debug(
                "Translating segment id={} format={} shownLength={} extraInstruction={} lowerTemperature={}",
                segment.id(),
                format,
                attempt.shownText().length(),
                !extraInstruction.isEmpty(),
                lowerTemperature);
        return send(attempt, DraftStep.DRAFT, "", "");
    }

    // A structural repair follows only a draft, a placeholder repair a draft or a structural repair, so the step tells
    // which repairs are already used.
    private Result<DraftOutcome> send(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final ChatRequest request = requestFor(attempt, step, rejected, diagnostic);
        final Result<ChatResponse> reply = DraftCalls.call(step, attempt.segment(), request, calls);
        if (reply.isErr()) {
            return decideModelError(attempt, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(
                attempt,
                Objects.requireNonNull(reply.data()),
                step != DraftStep.DRAFT,
                step == DraftStep.PLACEHOLDER_REPAIR);
    }

    /** The run's draft of one segment, in pieces when it is alone above the budget: no note, the draft's temperature. */
    public Result<DraftOutcome> translateSplit(
            final Segment segment,
            final DraftContext context,
            final ProtectedMask mask,
            final SentenceSplitter splitter,
            final int budgetTokens) {
        return translateSplit(segment, context, mask, splitter, budgetTokens, "", false);
    }

    /**
     * Drafts a segment, in sentence-aligned pieces when the text it is shown is alone above the budget. The pieces are
     * joined and gated once as that one segment, because the translation goes back into the node it came from. A
     * review retry passes the person's note and its temperature choice, so every piece's draft carries them as the
     * whole segment's draft would.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null context each piece is shown
     * @param mask the non-null spans hidden in the segment; the pieces are cut from its text
     * @param splitter the non-null sentence splitter of the source language
     * @param budgetTokens the chunk budget a piece must fit
     * @param extraInstruction the non-null note shown under {@code [Extra instruction]}; empty for none
     * @param lowerTemperature whether each draft call asks for its lower temperature
     * @return the draft's outcome, or the error a call answered, which the run routes
     */
    public Result<DraftOutcome> translateSplit(
            final Segment segment,
            final DraftContext context,
            final ProtectedMask mask,
            final SentenceSplitter splitter,
            final int budgetTokens,
            final String extraInstruction,
            final boolean lowerTemperature) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(splitter, "splitter");
        Objects.requireNonNull(extraInstruction, "extraInstruction");
        final String shown = mask.maskedText();
        final int estimate = TokenEstimator.estimate(shown, promptBuilder.sourceLanguage());
        if (estimate <= budgetTokens) {
            log.debug(
                    "Segment id={} estimate={} within budget={}, drafted whole", segment.id(), estimate, budgetTokens);
            return translate(segment, context, mask, extraInstruction, lowerTemperature);
        }
        final DraftAttempt whole = DraftAttempt.of(segment, context, mask, extraInstruction, lowerTemperature);
        return switch (OversizedSplit.plan(segment, shown, promptBuilder.sourceLanguage(), budgetTokens, splitter)) {
            case OversizedSplit.Pieces plan -> translatePieces(whole, plan.pieces());
            case OversizedSplit.Unsplittable none -> unsplittable(whole, mask, estimate, budgetTokens);
        };
    }

    // A segment whose one sentence is alone above the budget cannot be cut, so it is drafted whole, without preceding
    // targets or memory hits to leave it room, keeping the note and temperature the draft was asked with.
    private Result<DraftOutcome> unsplittable(
            final DraftAttempt attempt, final ProtectedMask mask, final int estimate, final int budgetTokens) {
        final Segment segment = attempt.segment();
        final DraftContext context = attempt.context();
        log.warn(
                "Oversized segment {} cannot be split estimate={} budget={}; drafting it whole without"
                        + " preceding targets or memory hits",
                segment.id(),
                estimate,
                budgetTokens);
        return translate(
                segment,
                new DraftContext(List.of(), context.summary(), context.glossaryLines(), List.of()),
                mask,
                attempt.extraInstruction(),
                attempt.lowerTemperature());
    }

    /** Drafts each piece with the segment's own context, joins the replies in order and gates them once. */
    private Result<DraftOutcome> translatePieces(final DraftAttempt attempt, final List<String> pieces) {
        final Segment segment = attempt.segment();
        final DraftContext context = attempt.context();
        log.debug("Translating segment id={} in {} pieces", segment.id(), pieces.size());
        final PieceRedraft redraft = (findings, redraftCalls) ->
                PieceDrafter.redraft(this, replyParser, segment, context, pieces, findings, redraftCalls);
        final DraftAttempt whole = attempt.redraftedBy(redraft);
        final Result<PieceDrafter.Piece> drafted = new PieceDrafter(
                        this, replyParser, calls, attempt.extraInstruction(), attempt.lowerTemperature())
                .draftAll(segment, context, pieces);
        if (drafted.isErr()) {
            return decideModelError(whole, Objects.requireNonNull(drafted.error()));
        }
        return switch (Objects.requireNonNull(drafted.data())) {
            case PieceDrafter.Unusable(final AppError unusable) ->
                DraftOutcomes.flaggedAtOnce(whole, unusable, FinishReason.STOP.name(), "[]");
            case PieceDrafter.Text(final String joined) -> restore(whole, joined, true);
        };
    }

    ChatRequest requestFor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        final OutputLimit limit = OutputLimit.forSource(
                attempt.shownText(), promptBuilder.sourceLanguage(), promptBuilder.targetLanguage());
        final boolean lower = step == DraftStep.DRAFT && attempt.lowerTemperature();
        final ChatRequest request =
                ChatRequests.build(step.promptName(), messagesFor(attempt, step, rejected, diagnostic), limit, lower);
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
        final String extra = attempt.extraInstruction();
        return switch (step) {
            case DRAFT -> promptBuilder.messagesFor(segment, context, shown, extra);
            case STRUCTURAL_REPAIR ->
                promptBuilder.messagesForStructuredRepair(segment, context, shown, extra, rejected, diagnostic);
            case PLACEHOLDER_REPAIR ->
                promptBuilder.messagesForPlaceholderRepair(
                        segment, context, shown, extra, rejected, diagnostic.isEmpty() ? null : diagnostic);
        };
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
            return DraftOutcomes.unfinished(attempt, response);
        }
        final ParsedReply parsed = replyParser.parse(response.content());
        if (parsed.kind() == ReplyKind.INVALID_STRUCTURED) {
            return structuralRepairUsed
                    ? DraftOutcomes.invalidStructuredReply(attempt, response)
                    : repairStructured(attempt, response.content(), parsed.diagnostic());
        }
        final String trimmed = parsed.translation().strip();
        DraftOutcomes.traceReply(response.content(), trimmed);
        log.debug(
                "Model reply segment={} kind={} finish={} empty={} replyLength={} placeholderRepair={}",
                attempt.segment().id(),
                parsed.kind(),
                response.finishReason(),
                trimmed.isEmpty(),
                trimmed.length(),
                placeholderRepairUsed);
        return trimmed.isEmpty()
                ? DraftOutcomes.unfinished(attempt, response)
                : restore(attempt, trimmed, placeholderRepairUsed);
    }

    private Result<DraftOutcome> repairStructured(
            final DraftAttempt attempt, final String rejectedReply, final String diagnostic) {
        final Segment segment = attempt.segment();
        log.warn("Repairing invalid structured model reply segmentId={}", segment.id());
        return send(attempt, DraftStep.STRUCTURAL_REPAIR, rejectedReply, diagnostic);
    }

    private Result<DraftOutcome> restore(
            final DraftAttempt attempt, final String trimmed, final boolean placeholderRepairUsed) {
        final Segment segment = attempt.segment();
        log.debug("Restoring segment id={} format={} trimmedLength={}", segment.id(), format, trimmed.length());
        final String restoredWhitespace = WhitespaceRestoration.restore(segment.masked(), trimmed);
        logTraceRestoration(restoredWhitespace);
        return switch (gate.restore(segment, restoredWhitespace)) {
            case GateResult.Restored restored -> DraftOutcomes.drafted(attempt, restoredWhitespace, restored);
            case GateResult.GateFailed failed -> recover(attempt, restoredWhitespace, failed, placeholderRepairUsed);
            case GateResult.StepError stepError -> {
                log.debug(
                        "Gate completed segmentId={} result=StepError code={}",
                        segment.id(),
                        stepError.error().code());
                yield DraftOutcomes.routed(segment, stepError.error(), "unmask");
            }
        };
    }

    // The order for a reply the placeholder gate refused: put back what it lost without a call; then one model repair
    // told exactly which tokens are wrong; on that repair's reply, the same deterministic repair and then every token
    // placed again by position; only then the quality loop's directed fix, and failing that the source is kept.
    private Result<DraftOutcome> recover(
            final DraftAttempt attempt,
            final String rejected,
            final GateResult.GateFailed failed,
            final boolean placeholderRepairUsed) {
        final Segment segment = attempt.segment();
        log.debug(
                "Gate completed segmentId={} result=GateFailed code={} placeholderRepairUsed={}",
                segment.id(),
                failed.error().code(),
                placeholderRepairUsed);
        if (gate.restoreRepairing(segment, rejected, PlaceholderRepair.RESTORE_MISSING)
                instanceof GateResult.Restored restored) {
            return DraftOutcomes.drafted(attempt, rejected, restored);
        }
        if (!placeholderRepairUsed) {
            return repairPlaceholder(attempt, rejected, failed);
        }
        if (gate.restoreRepairing(segment, rejected, PlaceholderRepair.REWRAP_ALL)
                instanceof GateResult.Restored restored) {
            return DraftOutcomes.drafted(attempt, rejected, restored);
        }
        return DraftOutcomes.stillFailingTheGate(attempt, rejected, failed);
    }

    // A repair whose own reply is unusable leaves the draft's reply, which the gate refused only for its markup: its
    // words are kept and every token is placed again by position before the segment is given up.
    private Result<DraftOutcome> repairPlaceholder(
            final DraftAttempt attempt, final String rejectedTarget, final GateResult.GateFailed failed) {
        final Segment segment = attempt.segment();
        final String note = GateNotes.describe(
                attempt.shownText(),
                rejectedTarget,
                segment.pairs(),
                failed.finding().note());
        log.warn("Repairing placeholder mismatch segmentId={}", segment.id());
        final Result<DraftOutcome> repaired = send(attempt, DraftStep.PLACEHOLDER_REPAIR, rejectedTarget, note);
        if (repaired.isOk()
                && repaired.data() instanceof DraftOutcome.FlaggedAtOnce
                && gate.restoreRepairing(segment, rejectedTarget, PlaceholderRepair.REWRAP_ALL)
                        instanceof GateResult.Restored restored) {
            log.info("Placeholder repair reply unusable; draft kept with re-placed tokens segmentId={}", segment.id());
            return DraftOutcomes.drafted(attempt, rejectedTarget, restored);
        }
        return repaired;
    }

    private static void logTraceRestoration(final String restored) {
        if (log.isTraceEnabled()) {
            log.trace("Segment reply restored={}", restored);
        }
    }
}
