package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.BatchReplyParser;
import ua.bookloom.pipeline.batch.ItemOutcome;
import ua.bookloom.pipeline.batch.ItemStatus;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

/**
 * Scores a reply to a logged call with the run's own classes: a draft reply by the draft reply parser and
 * {@link ReplyJudge}, a batch reply by the batch parser and the judge on each item. A call of any other kind has no
 * judge here and is scored on its structure only: a reply, and a JSON object when the request asked for one. The detail
 * words hold counts and verdicts, never book text.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReplayScoring {

    /**
     * What a reply came to.
     *
     * @param passed whether every part of it is what the run would accept
     * @param refused how many items (one for a single draft) the run would not accept
     * @param detail a few words, counts and verdicts only
     */
    record Scored(boolean passed, int refused, String detail) {}

    private static final ModelCalls NO_CALLS = (kind, segmentId, request) ->
            Result.err(AppError.of(ErrorCode.internal, "no call", "scoring a reply sends no model call"));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BatchReplyParser BATCH_PARSER = new BatchReplyParser(MAPPER);

    static Scored score(final LoggedCall call, final String reply) {
        if (call.kind() != CallKind.DRAFT || call.sourceTexts().isEmpty()) {
            return structure(call, reply);
        }
        return call.isBatch() ? batch(call, reply) : single(call, reply);
    }

    /** The project a draft call stands for, built from the source texts the log shows; the glossary and context are not in a log. */
    static EvalProject project(final LoggedCall call) {
        return EvalProject.of(
                new EvalProject.Setup(
                        call.sourceLanguage(),
                        call.targetLanguage(),
                        null,
                        List.of(),
                        EvalContext.none(),
                        call.sourceTexts(),
                        List.of()),
                NO_CALLS);
    }

    private static Scored single(final LoggedCall call, final String reply) {
        final ReplyJudge.Verdict verdict = ReplyJudge.judgeReply(project(call), 0, reply);
        final String word = verdict.accepted() ? "accepted" : "refused";
        return new Scored(verdict.accepted(), verdict.accepted() ? 0 : 1, "single " + word);
    }

    private static Scored batch(final LoggedCall call, final String reply) {
        final EvalProject project = project(call);
        final Optional<PreparedBatch> prepared = project.batch();
        if (prepared.isEmpty()) {
            return new Scored(false, call.sourceTexts().size(), "batch does not fit the window");
        }
        final List<BatchItem> items = prepared.get().items();
        final BatchReply parsed = BATCH_PARSER.parse(reply, items, call.sourceLanguage(), call.targetLanguage());
        int accepted = 0;
        for (int i = 0; i < items.size(); i++) {
            final ItemOutcome outcome = parsed.outcome(items.get(i).id()).orElseThrow();
            if (outcome.status() == ItemStatus.OK
                    && ReplyJudge.judge(project, i, outcome.target()).accepted()) {
                accepted++;
            }
        }
        return new Scored(
                accepted == items.size(),
                items.size() - accepted,
                "batch of " + items.size() + ": accepted " + accepted + (parsed.readable() ? "" : ", unreadable"));
    }

    private static Scored structure(final LoggedCall call, final String reply) {
        final boolean wantsObject = call.chatRequest().responseFormat() != null;
        final boolean ok = !reply.isBlank() && (!wantsObject || isObject(reply));
        return new Scored(ok, ok ? 0 : 1, "structure only: " + (ok ? "ok" : "not a JSON object"));
    }

    private static boolean isObject(final String reply) {
        try {
            final JsonNode node = MAPPER.readTree(reply);
            return node != null && node.isObject();
        } catch (IOException e) {
            return false;
        }
    }
}
