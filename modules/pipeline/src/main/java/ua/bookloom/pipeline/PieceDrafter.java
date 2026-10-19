package ua.bookloom.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.GateNotes;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.PauseDecider;

/**
 * Drafts one piece of an oversized segment: one call, the draft step's one structural repair, and a check that the
 * reply holds exactly the piece's own tokens with one placeholder repair. Whole-segment unmasking and the decision
 * stay with {@link SegmentTranslator}, because the pieces only make sense joined. One drafter drafts every piece
 * under the same extra instruction and temperature choice, through the same seam.
 */
@Slf4j
final class PieceDrafter {

    private static final String PIECE_TOKENS_MISMATCH = "The piece's placeholder tokens do not match its source.";

    /** What one piece's draft came to: text to join, or a reply the step itself found unusable. */
    sealed interface Piece permits Text, Unusable {}

    /** The piece's trimmed translation. */
    record Text(String value) implements Piece {}

    /**
     * A reply the model gave but the step cannot use, so the segment is flagged. It is kept apart from a model-call
     * error, which the run's routing decides, because a piece's own {@code validation} must still flag.
     */
    record Unusable(AppError error) implements Piece {}

    private final SegmentTranslator owner;
    private final DraftReplyParser replyParser;
    private final ModelCalls calls;
    private final String extraInstruction;
    private final boolean lowerTemperature;

    PieceDrafter(
            final SegmentTranslator owner,
            final DraftReplyParser replyParser,
            final ModelCalls calls,
            final String extraInstruction,
            final boolean lowerTemperature) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.replyParser = Objects.requireNonNull(replyParser, "replyParser");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.extraInstruction = Objects.requireNonNull(extraInstruction, "extraInstruction");
        this.lowerTemperature = lowerTemperature;
    }

    /**
     * Drafts every piece again, each told to fix the findings — their notes, one line each — and hands the joined
     * result back as a rewrite of the whole segment, which the repair round gates and evaluates once like any other.
     *
     * @return the rewrite, a flag-now reply when a piece's answer is unusable or its error flags at once, or the error
     *     a call answered that the run routes
     */
    static Result<RepairReply> redraft(
            final SegmentTranslator owner,
            final DraftReplyParser replyParser,
            final Segment segment,
            final DraftContext context,
            final List<String> pieces,
            final List<QaFinding> findings,
            final ModelCalls calls) {
        final String instruction = findings.stream().map(QaFinding::note).collect(Collectors.joining("\n"));
        log.debug("Redrafting segment id={} pieces={} findings={}", segment.id(), pieces.size(), findings.size());
        if (log.isTraceEnabled()) {
            log.trace("Extra instruction segmentId={} instruction={}", segment.id(), instruction);
        }
        final Result<Piece> drafted =
                new PieceDrafter(owner, replyParser, calls, instruction, false).draftAll(segment, context, pieces);
        if (drafted.isErr()) {
            final AppError error = Objects.requireNonNull(drafted.error());
            return PauseDecider.route(error.code()) == PauseDecider.Route.FLAG_AT_ONCE
                    ? Result.ok(new RepairReply.FlagNow(error))
                    : Result.err(error);
        }
        return Result.ok(
                switch (Objects.requireNonNull(drafted.data())) {
                    case Unusable(final AppError unusable) -> new RepairReply.FlagNow(unusable);
                    case Text(final String joined) -> new RepairReply.Rewritten(joined);
                });
    }

    /**
     * Drafts every piece in order and joins the replies.
     *
     * @return the joined, stripped translation, the first piece's reply the step cannot use, or the error a call
     *     answered
     */
    Result<Piece> draftAll(final Segment segment, final DraftContext context, final List<String> pieces) {
        log.debug(
                "Drafting pieces segmentId={} pieces={} hasExtraInstruction={} lowerTemperature={}",
                segment.id(),
                pieces.size(),
                !extraInstruction.isEmpty(),
                lowerTemperature);
        final List<String> replies = new ArrayList<>();
        for (final String piece : pieces) {
            final Result<Piece> drafted = draft(pieceOf(segment, piece), context);
            if (drafted.isErr()) {
                return drafted;
            }
            switch (Objects.requireNonNull(drafted.data())) {
                case Unusable unusable -> {
                    return drafted;
                }
                case Text(final String text) -> replies.add(WhitespaceRestoration.restore(piece, text));
            }
        }
        return Result.ok(new Text(String.join("", replies).strip()));
    }

    /** A copy of the segment whose masked text is one piece, so the prompt states that piece's own tokens. */
    static Segment pieceOf(final Segment segment, final String piece) {
        return new Segment(
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                segment.sourceInner(),
                piece,
                segment.placeholders(),
                segment.sourceHash(),
                segment.prevKey(),
                segment.nextKey(),
                segment.anchor(),
                segment.targetInner(),
                segment.status(),
                segment.confidence(),
                segment.declaredLanguage(),
                segment.pairs(),
                segment.lineBreakTokens());
    }

    /** The piece's draft, or the error the model call answered. */
    private Result<Piece> draft(final Segment piece, final DraftContext context) {
        log.debug(
                "Drafting piece segmentId={} pieceLength={}",
                piece.id(),
                piece.masked().length());
        if (log.isTraceEnabled()) {
            log.trace("Piece text segmentId={} text={}", piece.id(), piece.masked());
        }
        return answer(piece, context, callFor(piece, context, DraftStep.DRAFT, "", ""), false, false);
    }

    private Result<Piece> answer(
            final Segment piece,
            final DraftContext context,
            final Result<ChatResponse> reply,
            final boolean structuralUsed,
            final boolean placeholderUsed) {
        if (reply.isErr()) {
            return Result.err(Objects.requireNonNull(reply.error()));
        }
        final ChatResponse response = Objects.requireNonNull(reply.data());
        final ParsedReply parsed = replyParser.parse(response.content(), piece.masked(), owner.targetLanguage());
        if (parsed.kind() == ReplyKind.INVALID_STRUCTURED && !response.content().isBlank()) {
            return structuralUsed
                    ? failed(piece, ErrorCode.validation, "The model did not return the required translation JSON.")
                    : repair(
                            piece,
                            context,
                            DraftStep.STRUCTURAL_REPAIR,
                            response.content(),
                            parsed.diagnostic(),
                            placeholderUsed);
        }
        return accept(piece, context, response, parsed.translation().strip(), placeholderUsed);
    }

    private Result<Piece> accept(
            final Segment piece,
            final DraftContext context,
            final ChatResponse response,
            final String trimmed,
            final boolean placeholderUsed) {
        if (log.isTraceEnabled()) {
            log.trace("Piece reply segmentId={} raw={} trimmed={}", piece.id(), response.content(), trimmed);
        }
        if (trimmed.isEmpty()) {
            return failed(piece, ErrorCode.emptyCompletion, "The model returned no translated text.");
        }
        if (response.finishReason() != FinishReason.STOP) {
            return failed(piece, ErrorCode.validation, "The model response did not finish normally.");
        }
        if (keepsItsTokens(trimmed, Tokens.inOrder(piece.masked()))) {
            return Result.ok(new Text(trimmed));
        }
        return placeholderUsed
                ? failed(piece, ErrorCode.validation, PIECE_TOKENS_MISMATCH)
                : repair(
                        piece,
                        context,
                        DraftStep.PLACEHOLDER_REPAIR,
                        trimmed,
                        GateNotes.describe(piece.masked(), trimmed, piece.pairs(), PIECE_TOKENS_MISMATCH),
                        false);
    }

    /**
     * Whether a piece's reply holds its own tokens in order, apart from tokens it invented glued to a word, which the
     * whole segment's deterministic repair drops while keeping the word. An invented token standing for a word, or
     * a missing, repeated or moved own token, sends the piece to its repair.
     */
    private static boolean keepsItsTokens(final String reply, final List<String> expected) {
        final List<String> own =
                Tokens.inOrder(reply).stream().filter(expected::contains).toList();
        final boolean keeps = own.equals(expected)
                && Tokens.inventedStandingAlone(reply, expected).isEmpty();
        log.debug("Piece tokens kept={} expected={} observed={}", keeps, expected, Tokens.inOrder(reply));
        return keeps;
    }

    private Result<Piece> repair(
            final Segment piece,
            final DraftContext context,
            final DraftStep step,
            final String rejected,
            final String diagnostic,
            final boolean placeholderUsed) {
        log.debug("Repairing piece segmentId={} step={}", piece.id(), step);
        log.warn("Repairing a piece of an oversized segment segmentId={} step={}", piece.id(), step);
        final boolean isPlaceholder = step == DraftStep.PLACEHOLDER_REPAIR;
        return answer(
                piece,
                context,
                callFor(piece, context, step, rejected, diagnostic),
                !isPlaceholder,
                isPlaceholder || placeholderUsed);
    }

    private Result<ChatResponse> callFor(
            final Segment piece,
            final DraftContext context,
            final DraftStep step,
            final String rejected,
            final String diagnostic) {
        final DraftAttempt attempt = DraftAttempt.ofPiece(piece, context, extraInstruction, lowerTemperature);
        final var request = owner.requestFor(attempt, step, rejected, diagnostic);
        return DraftCalls.call(
                step, piece, request, owner.requests().descriptor(attempt, step, rejected, diagnostic), calls);
    }

    private static Result<Piece> failed(final Segment piece, final ErrorCode code, final String message) {
        log.debug("Piece failed segmentId={} code={}", piece.id(), code);
        return Result.ok(new Unusable(AppError.of(code, "Piece translation failed", message)));
    }
}
