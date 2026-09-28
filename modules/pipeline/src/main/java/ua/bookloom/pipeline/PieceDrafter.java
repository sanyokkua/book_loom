package ua.bookloom.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * Drafts one piece of an oversized segment: one call, the draft step's one structural repair, and a check that the
 * reply holds exactly the piece's own tokens with one placeholder repair. Whole-segment unmasking and the decision
 * stay with {@link SegmentTranslator}, because the pieces only make sense joined.
 */
@Slf4j
final class PieceDrafter {

    private static final Pattern PLACEHOLDER = Pattern.compile("⟦g\\d+⟧");

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

    /** The piece's trimmed translation, or the error that flags or ends the segment. */
    Result<String> draft(final Segment piece, final DraftContext context) {
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
                owner.callModel(piece, owner.requestFor(piece, context, PromptName.DRAFT, "", "")),
                false,
                false);
    }

    private Result<String> answer(
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
                            PromptName.STRUCTURAL_REPAIR,
                            response.content(),
                            parsed.diagnostic(),
                            placeholderUsed);
        }
        return accept(piece, context, response, parsed.translation().strip(), placeholderUsed);
    }

    private Result<String> accept(
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
        if (tokensOf(trimmed).equals(tokensOf(piece.masked()))) {
            return Result.ok(trimmed);
        }
        return placeholderUsed
                ? failed(piece, ErrorCode.validation, "The piece's placeholder tokens do not match its source.")
                : repair(piece, context, PromptName.PLACEHOLDER_REPAIR, trimmed, "", false);
    }

    private Result<String> repair(
            final Segment piece,
            final DraftContext context,
            final PromptName kind,
            final String rejected,
            final String diagnostic,
            final boolean placeholderUsed) {
        log.debug("Repairing piece segmentId={} kind={}", piece.id(), kind);
        log.warn("Repairing a piece of an oversized segment segmentId={} kind={}", piece.id(), kind);
        final boolean isPlaceholder = kind == PromptName.PLACEHOLDER_REPAIR;
        return answer(
                piece,
                context,
                owner.callModel(piece, owner.requestFor(piece, context, kind, rejected, diagnostic)),
                !isPlaceholder,
                isPlaceholder || placeholderUsed);
    }

    private static Result<String> failed(final Segment piece, final ErrorCode code, final String message) {
        log.debug("Piece failed segmentId={} code={}", piece.id(), code);
        return Result.err(AppError.of(code, "Piece translation failed", message));
    }

    private static List<String> tokensOf(final String text) {
        final Matcher matcher = PLACEHOLDER.matcher(text);
        final List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}
