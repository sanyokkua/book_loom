package ua.bookloom.pipeline.heal;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;

/**
 * The one place a self-heal call's raw model reply becomes a {@link RepairReply}, applying design D3 rules 2-3 to
 * every self-heal call so {@link DirectedFix} and backward
 * revision's re-render read a reply the same way instead of each reimplementing the same checks.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RepairReplies {

    /**
     * Classifies one self-heal call's outcome.
     *
     * @param reply the model call's result
     * @param parser the strict single-target reader
     * @return a usable rewrite, a malformed reply, or content that flags the segment now, each wrapped in
     *     {@link Result#ok}; the call's own error, unwrapped, when it was neither {@code emptyCompletion} nor
     *     {@code contextWindow}
     */
    public static Result<RepairReply> read(final Result<ChatResponse> reply, final DraftReplyParser parser) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(parser, "parser");
        if (reply.isErr()) {
            return routeCallFailure(Objects.requireNonNull(reply.error()));
        }
        final ChatResponse response = Objects.requireNonNull(reply.data());
        if (response.content().isBlank()) {
            return Result.ok(new RepairReply.FlagNow(emptyCompletion()));
        }
        if (response.finishReason() != FinishReason.STOP) {
            return Result.ok(new RepairReply.FlagNow(invalidFinish()));
        }
        final ParsedReply parsed = parser.parse(response.content());
        return parsed.kind() == ReplyKind.INVALID_STRUCTURED
                ? Result.ok(new RepairReply.Malformed(parsed.diagnostic()))
                : Result.ok(new RepairReply.Rewritten(parsed.translation().strip()));
    }

    private static Result<RepairReply> routeCallFailure(final AppError error) {
        return error.code() == ErrorCode.emptyCompletion || error.code() == ErrorCode.contextWindow
                ? Result.ok(new RepairReply.FlagNow(error))
                : Result.err(error);
    }

    private static AppError emptyCompletion() {
        return AppError.of(ErrorCode.emptyCompletion, "Empty model response", "The model returned no repaired text.");
    }

    private static AppError invalidFinish() {
        return AppError.of(
                ErrorCode.validation, "Incomplete model response", "The model response did not finish normally.");
    }
}
