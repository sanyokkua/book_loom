package ua.bookloom.pipeline.heal;

import java.util.Objects;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ParsedReply;
import ua.bookloom.pipeline.prompt.DraftReplyParser.ReplyKind;
import ua.bookloom.pipeline.run.PauseDecider;

/**
 * The one place a self-heal call's raw model reply becomes a {@link RepairReply}, applying design D3 rules 2-3 to
 * every self-heal call so the repair and the re-render read a reply the same way instead of each reimplementing the
 * same checks.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RepairReplies {

    /**
     * Classifies one self-heal call's outcome with the reader of the reply's shape the call asked for.
     *
     * @param reply the model call's result
     * @param reader the non-null reader of the raw reply text: the single-target reader or a batch of one's
     * @return a usable rewrite, a malformed reply, or content that flags the segment now, each wrapped in
     *     {@link Result#ok}; the call's own error, unwrapped, when {@link PauseDecider#route(AppError)} does not flag
     *     it at once
     */
    public static Result<RepairReply> read(
            final Result<ChatResponse> reply, final Function<String, ParsedReply> reader) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(reader, "reader");
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
        final ParsedReply parsed = reader.apply(response.content());
        return parsed.kind() == ReplyKind.INVALID_STRUCTURED
                ? Result.ok(new RepairReply.Malformed(parsed.diagnostic()))
                : Result.ok(new RepairReply.Rewritten(parsed.translation().strip()));
    }

    private static Result<RepairReply> routeCallFailure(final AppError error) {
        return PauseDecider.route(error) == PauseDecider.Route.FLAG_AT_ONCE
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
