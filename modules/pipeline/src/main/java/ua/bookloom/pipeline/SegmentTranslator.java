package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.chunk.OversizedSplit;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.PieceRedraft;
import ua.bookloom.pipeline.heal.RepairCall;
import ua.bookloom.pipeline.heal.RepairReplies;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The draft step: one draft call, with at most one format re-ask, whose reply is read by design D3's reply
 * precedence; marks the reply lost are put back by code, and a reply that copies a shown example is asked once more
 * and then flagged. It decides nothing — a reply it can use goes to the quality loop, which accepts, repairs or flags it
 * — so the chunk runner and a review retry share one draft step. A run's draft is a batch of one planned by the context
 * builder; a review retry still replays its stored single-draft prompt (task 11.7 moves it).
 */
@Slf4j
public final class SegmentTranslator {

    private static final String SEGMENT = "segment";
    private static final String CONTEXT = "context";

    /** The error of a segment whose reply copied a shown example twice. */
    static final String EXAMPLE_COPIED = DraftReplies.EXAMPLE_COPIED;

    private final GateFunction gate;
    private final ModelCalls calls;
    private final BookFormat format;
    private final DraftPrompts prompts;
    private final DraftRequests requests;
    private final DraftReplies replies;

    /**
     * Creates a review retry's draft step over its placeholder gate, call seam, format, single-draft prompts and reply
     * reader.
     */
    public SegmentTranslator(
            final GateFunction gate,
            final ModelCalls calls,
            final BookFormat format,
            final DraftPromptBuilder promptBuilder,
            final DraftReplyParser replyParser) {
        this(gate, calls, format, new SingleDraftPrompts(promptBuilder, replyParser));
    }

    /**
     * Creates the draft step of one run, whose drafts are planned by the run's context builder as batches of one.
     *
     * @param gate the non-null placeholder gate
     * @param calls the non-null seam every call goes through
     * @param format the non-null format of the book
     * @param planner the non-null planner of the run's translate-family calls
     * @param mapper the non-null tolerant JSON mapper a batch of one's reply is read with
     */
    public SegmentTranslator(
            final GateFunction gate,
            final ModelCalls calls,
            final BookFormat format,
            final TranslatePlanner planner,
            final ObjectMapper mapper) {
        this(gate, calls, format, new TranslateDraftPrompts(planner, mapper));
    }

    private SegmentTranslator(
            final GateFunction gate, final ModelCalls calls, final BookFormat format, final DraftPrompts prompts) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.format = Objects.requireNonNull(format, "format");
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.requests = new DraftRequests(gate, prompts);
        this.replies = new DraftReplies(gate, format, prompts, calls, this::send);
    }

    Result<DraftOutcome> translate(final Segment segment) {
        return translate(segment, DraftContext.empty(), ProtectedMask.none(segment.masked()));
    }

    /**
     * A run's draft of one segment, planned over the window, map and memory it is shown.
     *
     * @param segment the non-null segment to draft
     * @param context the window, map and memory the draft is shown, or null to plan it with none
     * @param mask the non-null spans hidden in the segment
     * @return the draft's outcome, or the error a call answered, which the run routes
     */
    public Result<DraftOutcome> translate(
            final Segment segment, @Nullable final TranslateContext context, final ProtectedMask mask) {
        Objects.requireNonNull(segment, SEGMENT);
        Objects.requireNonNull(mask, "mask");
        return draft(DraftAttempt.of(segment, context, mask, "", false));
    }

    /**
     * The same draft step restoring every reply through {@code chunkGate}, which knows the protected spans of one
     * chunk's segments.
     *
     * @param chunkGate the non-null gate of the chunk about to be drafted
     * @return a draft step sharing this one's seam, format, prompts and reply reader
     */
    public SegmentTranslator gatedBy(final GateFunction chunkGate) {
        return new SegmentTranslator(Objects.requireNonNull(chunkGate, "chunkGate"), calls, format, prompts);
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
        Objects.requireNonNull(segment, SEGMENT);
        Objects.requireNonNull(context, CONTEXT);
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(extraInstruction, "extraInstruction");
        return draft(DraftAttempt.of(segment, context, mask, extraInstruction, lowerTemperature));
    }

    private Result<DraftOutcome> draft(final DraftAttempt attempt) {
        log.debug(
                "Translating segment id={} format={} shownLength={} extraInstruction={} lowerTemperature={} planned={}",
                attempt.segment().id(),
                format,
                attempt.shownText().length(),
                !attempt.extraInstruction().isEmpty(),
                attempt.lowerTemperature(),
                attempt.translate() != null);
        return send(attempt, DraftStep.DRAFT, "", "", DraftReplies.Sent.FIRST);
    }

    Result<DraftOutcome> send(
            final DraftAttempt attempt,
            final DraftStep step,
            final String rejected,
            final String diagnostic,
            final DraftReplies.Sent sent) {
        final ChatRequest built = requestFor(attempt, step, rejected, diagnostic);
        final ChatRequest request = sent.echoAsked() ? built.withSeed(ChatRequests.RETRY_SEED) : built;
        final Result<ChatResponse> reply = DraftCalls.call(
                step, attempt.segment(), request, requests.descriptor(attempt, step, rejected, diagnostic), calls);
        if (reply.isErr()) {
            return replies.decideModelError(attempt, Objects.requireNonNull(reply.error()));
        }
        return replies.decideResponse(
                attempt,
                Objects.requireNonNull(reply.data()),
                new DraftReplies.Sent(sent.reasked() || step != DraftStep.DRAFT, sent.echoAsked()));
    }

    /**
     * How the one repair of a segment drafted whole is sent: the draft's own prompt and context, the previous attempt
     * and its defects, at the repair's temperature with the retry seed; with no defect, the plain draft again with the
     * retry seed (Fast's re-draft of lost marks).
     *
     * @param segment the non-null segment the draft is for
     * @param context the window, map and memory the draft was shown, or null when it was planned with none
     * @param mask the non-null spans hidden in the segment
     * @return the segment's repair call
     */
    public RepairCall repairCall(
            final Segment segment, @Nullable final TranslateContext context, final ProtectedMask mask) {
        final DraftAttempt attempt = DraftAttempt.of(
                Objects.requireNonNull(segment, SEGMENT), context, Objects.requireNonNull(mask, "mask"), "", false);
        return (previous, defects, through) -> repair(attempt, previous, defects, through);
    }

    private Result<RepairReply> repair(
            final DraftAttempt attempt,
            final String previous,
            final List<QaFinding> defects,
            final ModelCalls through) {
        if (defects.isEmpty()) {
            return redraft(attempt, through);
        }
        final Segment segment = attempt.segment();
        final String lines = DraftRequests.defectLines(defects);
        log.debug("Sending the one repair segmentId={} defects={}", segment.id(), defects.size());
        final ChatRequest request = requests.build(attempt, DraftStep.REPAIR, previous, lines);
        final Result<ChatResponse> reply = DraftCalls.call(
                DraftStep.REPAIR,
                segment,
                request,
                requests.descriptor(attempt, DraftStep.REPAIR, previous, lines),
                through);
        return RepairReplies.read(reply, text -> prompts.parseRepair(text, attempt));
    }

    // Fast's one retry of formatting marks the code could not put back: the plain draft again, with the retry seed so
    // the model does not answer the same reply.
    private Result<RepairReply> redraft(final DraftAttempt attempt, final ModelCalls through) {
        final Segment segment = attempt.segment();
        log.debug("Sending the plain re-draft of lost marks segmentId={}", segment.id());
        final ChatRequest request =
                requests.build(attempt, DraftStep.DRAFT, "", "").withSeed(ChatRequests.RETRY_SEED);
        final Result<ChatResponse> reply = DraftCalls.call(
                DraftStep.DRAFT, segment, request, requests.descriptor(attempt, DraftStep.DRAFT, "", ""), through);
        return RepairReplies.read(reply, text -> prompts.parseRepair(text, attempt));
    }

    /**
     * Takes one batch item's target as the segment's draft, with none of the repairs a single draft makes.
     *
     * @param segment the non-null segment the item stands for
     * @param mask the non-null spans hidden in the segment
     * @param maskedTarget the non-null target the batch reply gave, whose tokens are in place
     * @return the draft's outcome, or empty when the gate refused the target, which is then drafted on its own
     */
    public Optional<DraftOutcome> adopt(final Segment segment, final ProtectedMask mask, final String maskedTarget) {
        return BatchAdoption.adopt(gate, segment, mask, maskedTarget);
    }

    /**
     * A run's draft of one segment, in pieces when it is alone above the budget: no note, the draft's temperature.
     *
     * @param segment the non-null segment to draft
     * @param context the window, map and memory the draft is shown, or null to plan it with none
     * @param mask the non-null spans hidden in the segment
     * @param splitter the non-null sentence splitter of the source language
     * @param budgetTokens the current-part budget a piece must fit
     * @return the draft's outcome, or the error a call answered, which the run routes
     */
    public Result<DraftOutcome> translateSplit(
            final Segment segment,
            @Nullable final TranslateContext context,
            final ProtectedMask mask,
            final SentenceSplitter splitter,
            final int budgetTokens) {
        return translateSplit(segment, context, mask, splitter, budgetTokens, false);
    }

    /**
     * As {@link #translateSplit(Segment, TranslateContext, ProtectedMask, SentenceSplitter, int)}, for a segment whose
     * batch answer copied a shown example: this draft is that answer's one re-ask, so a copy again flags the segment.
     *
     * @param copiedOnce whether the segment's batch answer copied a shown example
     */
    public Result<DraftOutcome> translateSplit(
            final Segment segment,
            @Nullable final TranslateContext context,
            final ProtectedMask mask,
            final SentenceSplitter splitter,
            final int budgetTokens,
            final boolean copiedOnce) {
        Objects.requireNonNull(segment, SEGMENT);
        Objects.requireNonNull(mask, "mask");
        final DraftAttempt attempt = DraftAttempt.of(segment, context, mask, "", false);
        if (copiedOnce) {
            log.debug("Drafting segment id={} as the re-ask of a batch answer that copied an example", segment.id());
            calls.steps().added(StepKind.RE_ASK, "re-ask", DraftReplies.ECHO_KEY + segment.id(), segment.id());
            return replies.echoReask(attempt, false);
        }
        return split(attempt, splitter, budgetTokens);
    }

    /**
     * Drafts a segment, in sentence-aligned pieces when the text it is shown is alone above the budget. The pieces are
     * joined and gated once as that one segment, because the translation goes back into the node it came from. A
     * review retry passes the person's note and its temperature choice, so every piece's draft carries them as the
     * whole segment's draft would.
     *
     * @param segment the non-null segment to draft
     * @param context the non-null replayed context each piece is shown
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
        Objects.requireNonNull(segment, SEGMENT);
        Objects.requireNonNull(context, CONTEXT);
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(extraInstruction, "extraInstruction");
        return split(
                DraftAttempt.of(segment, context, mask, extraInstruction, lowerTemperature), splitter, budgetTokens);
    }

    private Result<DraftOutcome> split(
            final DraftAttempt whole, final SentenceSplitter splitter, final int budgetTokens) {
        Objects.requireNonNull(splitter, "splitter");
        final Segment segment = whole.segment();
        final String shown = whole.shownText();
        final int estimate = TokenEstimator.estimate(shown, prompts.sourceLanguage());
        if (estimate <= budgetTokens) {
            log.debug(
                    "Segment id={} estimate={} within budget={}, drafted whole", segment.id(), estimate, budgetTokens);
            return draft(whole);
        }
        return switch (OversizedSplit.plan(segment, shown, prompts.sourceLanguage(), budgetTokens, splitter)) {
            case OversizedSplit.Pieces(var pieces) -> translatePieces(whole, pieces);
            case OversizedSplit.Unsplittable _ -> unsplittable(whole, estimate, budgetTokens);
        };
    }

    // A segment whose one sentence is alone above the budget cannot be cut, so it is drafted whole, without preceding
    // targets or memory hits to leave it room, keeping the note and temperature the draft was asked with.
    private Result<DraftOutcome> unsplittable(final DraftAttempt attempt, final int estimate, final int budgetTokens) {
        log.warn(
                "Oversized segment {} cannot be split estimate={} budget={}; drafting it whole without"
                        + " preceding targets or memory hits",
                attempt.segment().id(),
                estimate,
                budgetTokens);
        return draft(attempt.alone());
    }

    /** Drafts each piece with the segment's own context, joins the replies in order and gates them once. */
    private Result<DraftOutcome> translatePieces(final DraftAttempt attempt, final List<String> pieces) {
        final Segment segment = attempt.segment();
        log.debug("Translating segment id={} in {} pieces", segment.id(), pieces.size());
        final PieceRedraft redraft =
                (findings, redraftCalls) -> PieceDrafter.redraft(this, attempt, pieces, findings, redraftCalls);
        final DraftAttempt whole = attempt.redraftedBy(redraft);
        final Result<PieceDrafter.Piece> drafted = new PieceDrafter(
                        this, calls, attempt.extraInstruction(), attempt.lowerTemperature())
                .draftAll(attempt, pieces);
        if (drafted.isErr()) {
            return replies.decideModelError(whole, Objects.requireNonNull(drafted.error()));
        }
        return switch (Objects.requireNonNull(drafted.data())) {
            case PieceDrafter.Unusable(final AppError unusable) ->
                DraftOutcomes.flaggedAtOnce(whole, unusable, FinishReason.STOP.name(), "[]");
            case PieceDrafter.Text(final String joined) -> replies.restore(whole, joined);
        };
    }

    ChatRequest requestFor(
            final DraftAttempt attempt, final DraftStep step, final String rejected, final String diagnostic) {
        return requests.build(attempt, step, rejected, diagnostic);
    }

    String targetLanguage() {
        return prompts.targetLanguage();
    }

    /** How this draft step's requests are made and read. */
    DraftPrompts prompts() {
        return prompts;
    }

    /** The requests this draft step sends, which a prompt eval builds the same way. */
    public DraftRequests requests() {
        return requests;
    }
}
