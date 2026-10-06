package ua.bookloom.pipeline.reviewer;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The per-chunk reviewer call: one call reading every drafted pair of a batch that passed its hard gates, answered per
 * pair as {@code ok}, edits or a rewrite. Pairs are labelled {@code s1}..{@code sk} so a small model never has to keep
 * a real segment id straight. It samples at temperature zero with a fixed seed so the same batch gets the same answer,
 * and a structured call that timed out is sent once more without its response format, because on one provider a
 * schema-constrained call stalled where the same prompt answered in seconds.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ReviewerCall {

    /** The fixed sampling seed; the value is arbitrary, only its constancy matters. */
    static final int REVIEWER_SEED = 15;

    private final PromptTemplates templates;
    private final ReviewReplyParser parser;

    /**
     * Reviews one batch in a single call.
     *
     * @param pairs the batch's pairs, in document order; never empty
     * @param frame the run's language pair, style sheet and foreign-passage policy
     * @param glossaryPairs the glossary renderings the batch had to use, one {@code source → target} line each, or
     *     empty when none applies
     * @param pass which pass of the batch this is
     * @param calls the seam the call is sent through
     * @return the batch's verdict — {@link Result#ok} even for an unreadable reply or a call answered
     *     {@link ErrorCode#emptyCompletion}/{@link ErrorCode#contextWindow}, and an unavailable verdict for a call
     *     that timed out twice; {@link Result#err} for any other call failure, so a provider outage waits for the
     *     provider as a draft call does
     */
    public Result<ReviewVerdict> review(
            final List<ReviewedPair> pairs,
            final CallFrame frame,
            final List<String> glossaryPairs,
            final ReviewPass pass,
            final ModelCalls calls) {
        return review(pairs, frame, glossaryPairs, List.of(), pass, calls);
    }

    /**
     * Reviews one batch in a single call, telling the reviewer who the characters are.
     *
     * @param pairs the batch's pairs, in document order; never empty
     * @param frame the run's language pair, style sheet and foreign-passage policy
     * @param glossaryPairs the glossary renderings the batch had to use, one {@code source → target} line each
     * @param characters the characters present with their gender, one {@code name — gender} line each, or empty when
     *     the glossary knows none
     * @param pass which pass of the batch this is
     * @param calls the seam the call is sent through
     * @return as {@link #review(List, CallFrame, List, ReviewPass, ModelCalls)}
     */
    public Result<ReviewVerdict> review(
            final List<ReviewedPair> pairs,
            final CallFrame frame,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewPass pass,
            final ModelCalls calls) {
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(pairs, "pairs");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(glossaryPairs, "glossaryPairs");
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(calls, "calls");
        final List<String> segmentIds =
                pairs.stream().map(ReviewedPair::segmentId).toList();
        log.debug("Reviewing batch pairCount={} pass={} segmentIds={}", pairs.size(), pass, segmentIds);
        try {
            final Result<ChatResponse> reply = ask(pairs, frame, glossaryPairs, characters, pass, calls);
            return reply.isErr()
                    ? routeFailure(segmentIds, Objects.requireNonNull(reply.error()))
                    : Result.ok(read(
                            Objects.requireNonNull(reply.data()),
                            pairs,
                            frame,
                            glossaryPairs,
                            characters,
                            pass,
                            calls));
        } catch (Throwable cause) {
            return internalFailure(cause);
        }
    }

    private static Result<ReviewVerdict> internalFailure(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Review call failed", "The reviewer could not evaluate this batch.", null, cause);
        log.error("Unexpected review failure code={}", error.code(), cause);
        return Result.err(error);
    }

    private ReviewVerdict read(
            final ChatResponse response,
            final List<ReviewedPair> pairs,
            final CallFrame frame,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewPass pass,
            final ModelCalls calls) {
        return response.finishReason() == FinishReason.LENGTH
                ? readCut(pairs, response, frame, glossaryPairs, characters, pass, calls)
                : readVerdict(pairs.stream().map(ReviewedPair::segmentId).toList(), pairs, response);
    }

    private Result<ChatResponse> ask(
            final List<ReviewedPair> pairs,
            final CallFrame frame,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewPass pass,
            final ModelCalls calls) {
        final ChatRequest request = request(pairs, frame, glossaryPairs, characters, pass);
        logTraceMessages(request);
        return send(pairs.stream().map(ReviewedPair::segmentId).toList(), request, calls);
    }

    /**
     * A reply the output cap cut is never a reason to flag: the complete entries are kept, the pairs left unread are
     * asked about once more in a smaller batch, and any pair still unread is taken as {@code ok} — the deterministic
     * checks already passed it, and a flag would hand the person a segment nothing is known to be wrong with.
     */
    private ReviewVerdict readCut(
            final List<ReviewedPair> pairs,
            final ChatResponse response,
            final CallFrame frame,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewPass pass,
            final ModelCalls calls) {
        logTraceReply(response.content());
        final ReviewVerdict first = parser.parseSalvaging(response.content(), pairs);
        final List<ReviewedPair> unread = unreadOf(pairs, first);
        log.warn(
                "Review reply cut by the output cap read={} unread={}",
                first.items().size(),
                unread.size());
        if (unread.isEmpty()) {
            return first;
        }
        final Result<ChatResponse> again = ask(unread, frame, glossaryPairs, characters, pass, calls);
        if (again.isErr()) {
            log.warn("Re-asking the unread pairs failed; they are taken as ok count={}", unread.size());
            return first;
        }
        final ChatResponse second = Objects.requireNonNull(again.data());
        logTraceReply(second.content());
        final ReviewVerdict rest = parser.parseSalvaging(second.content(), unread);
        final List<ReviewItem> items = new ArrayList<>(first.items());
        items.addAll(rest.items());
        log.warn(
                "Pairs still unread after the re-ask are taken as ok count={}",
                unread.size() - rest.items().size());
        return ReviewVerdict.answered(items);
    }

    private static List<ReviewedPair> unreadOf(final List<ReviewedPair> pairs, final ReviewVerdict verdict) {
        return pairs.stream()
                .filter(pair -> verdict.itemFor(pair.segmentId()).isEmpty())
                .toList();
    }

    private Result<ChatResponse> send(
            final List<String> segmentIds, final ChatRequest request, final ModelCalls calls) {
        final Result<ChatResponse> first = calls.callAbout(CallKind.REVIEW, segmentIds, request);
        if (first.isOk() || Objects.requireNonNull(first.error()).code() != ErrorCode.timeout) {
            return first;
        }
        log.warn("Review call timed out; sending it once more without a response format segmentIds={}", segmentIds);
        return calls.callAbout(CallKind.REVIEW, segmentIds, request.withoutResponseFormat());
    }

    private ReviewVerdict readVerdict(
            final List<String> segmentIds, final List<ReviewedPair> pairs, final ChatResponse response) {
        logTraceReply(response.content());
        final ReviewVerdict verdict = parser.parse(response.content(), pairs);
        log.debug("Reviewed batch answers={} readable={}", verdict.items().size(), verdict.readable());
        if (!verdict.readable()) {
            log.warn("Unreadable review reply segmentIds={}", segmentIds);
        }
        return verdict;
    }

    private static Result<ReviewVerdict> routeFailure(final List<String> segmentIds, final AppError error) {
        if (error.code() == ErrorCode.emptyCompletion || error.code() == ErrorCode.contextWindow) {
            log.warn("Unreadable review reply segmentIds={} code={}", segmentIds, error.code());
            return Result.ok(ReviewVerdict.unreadable());
        }
        // The provider already retried the stalled call and so did this call; pausing the run on it a third time would
        // only wait for the same stall. An outage is different: the reviewer says nothing while the provider is down,
        // so the run waits for it.
        if (error.code() == ErrorCode.timeout) {
            log.warn(
                    "Reviewer unavailable; its segments are decided by the quality checks alone and flagged"
                            + " segmentIds={} code={}",
                    segmentIds,
                    error.code());
            return Result.ok(ReviewVerdict.unavailable(error));
        }
        log.debug("Review call failed segmentIds={} code={}", segmentIds, error.code());
        return Result.err(error);
    }

    private ChatRequest request(
            final List<ReviewedPair> pairs,
            final CallFrame frame,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewPass pass) {
        final String system = templates.renderSystem(PromptName.REVIEWER, frame).strip();
        final Map<String, String> userValues = new HashMap<>();
        userValues.put("pairs", renderPairs(pairs));
        userValues.put("glossaryTerms", String.join("\n", glossaryPairs));
        userValues.put("characters", String.join("\n", characters));
        userValues.put("passFocus", pass.instruction());
        final String user =
                templates.renderUser(PromptName.REVIEWER, userValues).strip();
        final List<ChatMessage> messages =
                List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
        final OutputLimit limit = OutputLimit.forReview(
                pairs.stream().map(ReviewedPair::maskedCandidate).toList(), frame.targetLanguage());
        return ChatRequests.build(PromptName.REVIEWER, messages, limit, false).withSeed(REVIEWER_SEED);
    }

    private static String renderPairs(final List<ReviewedPair> pairs) {
        final List<String> blocks = new ArrayList<>();
        for (int index = 0; index < pairs.size(); index++) {
            final ReviewedPair pair = pairs.get(index);
            blocks.add("<Pair id=\"" + ReviewerLabels.of(index) + "\"><Source>" + pair.maskedSource()
                    + "</Source><Candidate>" + pair.maskedCandidate() + "</Candidate></Pair>");
        }
        return String.join("\n", blocks);
    }

    private static void logTraceMessages(final ChatRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("Review messages {}", request.messages());
        }
    }

    private static void logTraceReply(final String reply) {
        if (log.isTraceEnabled()) {
            log.trace("Review raw reply {}", reply);
        }
    }
}
