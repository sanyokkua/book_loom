package ua.bookloom.pipeline;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.prompt.DraftStep;

/**
 * Drafts one piece of an oversized segment: one call, the draft step's one structural repair, and a check that the
 * reply holds exactly the piece's own tokens with one placeholder repair. Whole-segment unmasking and the decision
 * stay with {@link SegmentTranslator}, because the pieces only make sense joined.
 */
@Slf4j
final class PieceDrafter {

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

    PieceDrafter(final SegmentTranslator owner, final DraftReplyParser replyParser) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.replyParser = Objects.requireNonNull(replyParser, "replyParser");
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
    Result<Piece> draft(final Segment piece, final DraftContext context) {
        log.debug(
                "Drafting piece segmentId={} pieceLength={}",
                piece.id(),
                piece.masked().length());
        if (log.isTraceEnabled()) {
            log.trace("Piece text segmentId={} text={}", piece.id(), piece.masked());
        }
        return answer(
                piece,
                context,
                owner.callModel(piece, owner.requestFor(piece, context, DraftStep.DRAFT, "", "")),
                false,
                false);
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
        final ParsedReply parsed = replyParser.parse(response.content());
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
        if (Tokens.inOrder(trimmed).equals(Tokens.inOrder(piece.masked()))) {
            return Result.ok(new Text(trimmed));
        }
        return placeholderUsed
                ? failed(piece, ErrorCode.validation, "The piece's placeholder tokens do not match its source.")
                : repair(piece, context, DraftStep.PLACEHOLDER_REPAIR, trimmed, "", false);
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
                owner.callModel(piece, owner.requestFor(piece, context, step, rejected, diagnostic)),
                !isPlaceholder,
                isPlaceholder || placeholderUsed);
    }

    private static Result<Piece> failed(final Segment piece, final ErrorCode code, final String message) {
        log.debug("Piece failed segmentId={} code={}", piece.id(), code);
        return Result.ok(new Unusable(AppError.of(code, "Piece translation failed", message)));
    }
}
