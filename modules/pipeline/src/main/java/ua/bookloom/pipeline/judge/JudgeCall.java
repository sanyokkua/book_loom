package ua.bookloom.pipeline.judge;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The per-chunk judge call: one call scoring every drafted pair that passed its hard gates and was not reused,
 * labelled {@code s1}..{@code sk} in document order so a small model never has to keep a real segment id straight
 * (design D8, "Judge each chunk once when the quality dial enables the judge"). Guice constructs it for the
 * {@link ua.bookloom.pipeline.heal.QualityLoop} the engine injects; {@code templates} is the module's eager singleton,
 * {@code parser} its own tolerant reader.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class JudgeCall {

    private final PromptTemplates templates;
    private final JudgeReplyParser parser;

    /**
     * Judges one chunk's qualifying pairs in a single call.
     *
     * @param pairs the chunk's pairs, in document order; each becomes one local label
     * @param frame the run's language pair, style sheet and foreign-passage policy
     * @param glossaryTerms the glossary terms occurring in the chunk, or empty when none apply
     * @param calls the seam the call is sent through
     * @return the chunk's verdict — {@link Result#ok} even for an unreadable reply or a call answered
     *     {@link ErrorCode#emptyCompletion}/{@link ErrorCode#contextWindow}, and an unavailable verdict for a call
     *     answered {@link ErrorCode#timeout}/{@link ErrorCode#unreachable}; {@link Result#err} for any other call
     *     failure
     */
    public Result<JudgeVerdict> judge(
            final List<JudgedPair> pairs,
            final CallFrame frame,
            final List<String> glossaryTerms,
            final ModelCalls calls) {
        Objects.requireNonNull(pairs, "pairs");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(calls, "calls");
        final List<String> segmentIds =
                pairs.stream().map(JudgedPair::segmentId).toList();
        log.debug("Judging chunk pairCount={} labels={}", pairs.size(), labelsOf(pairs.size()));
        try {
            final ChatRequest request = ChatRequests.build(
                    PromptName.JUDGE,
                    messagesFor(pairs, frame, glossaryTerms),
                    OutputLimit.forJudge(pairs.size()),
                    false);
            logTraceMessages(request);
            final Result<ChatResponse> reply = calls.callAbout(CallKind.JUDGE, segmentIds, request);
            return reply.isErr()
                    ? routeFailure(segmentIds, Objects.requireNonNull(reply.error()))
                    : Result.ok(readVerdict(segmentIds, pairs, Objects.requireNonNull(reply.data())));
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Judge call failed", "The judge could not evaluate this chunk.", null, cause);
            log.error("Unexpected judge failure code={}", error.code(), cause);
            return Result.err(error);
        }
    }

    private JudgeVerdict readVerdict(
            final List<String> segmentIds, final List<JudgedPair> pairs, final ChatResponse response) {
        logTraceReply(response.content());
        final JudgeVerdict verdict = parser.parse(response.content(), pairs);
        log.debug(
                "Judged chunk score={} verdict={} findingCount={} deferralCount={} readable={}",
                verdict.score(),
                verdict.verdict(),
                verdict.findings().size(),
                verdict.deferrals().size(),
                verdict.readable());
        if (!verdict.readable()) {
            log.warn("Unreadable judge reply segmentIds={}", segmentIds);
        }
        return verdict;
    }

    private static Result<JudgeVerdict> routeFailure(final List<String> segmentIds, final AppError error) {
        if (error.code() == ErrorCode.emptyCompletion || error.code() == ErrorCode.contextWindow) {
            log.warn("Unreadable judge reply segmentIds={} code={}", segmentIds, error.code());
            return Result.ok(JudgeVerdict.unreadable());
        }
        // The provider already retried the call; pausing the run on it again would only wait for the same stall.
        if (error.code() == ErrorCode.timeout || error.code() == ErrorCode.unreachable) {
            log.warn(
                    "Judge unavailable; its segments are decided by the quality checks alone and flagged"
                            + " segmentIds={} code={}",
                    segmentIds,
                    error.code());
            return Result.ok(JudgeVerdict.unavailable(error));
        }
        log.debug("Judge call failed segmentIds={} code={}", segmentIds, error.code());
        return Result.err(error);
    }

    private List<ChatMessage> messagesFor(
            final List<JudgedPair> pairs, final CallFrame frame, final List<String> glossaryTerms) {
        final String system = templates
                .renderSystem(PromptName.JUDGE, frame.systemSlotValues())
                .strip();
        final Map<String, String> userValues = new HashMap<>();
        userValues.put("pairs", renderPairs(pairs));
        userValues.put("glossaryTerms", String.join("\n", glossaryTerms));
        final String user = templates.renderUser(PromptName.JUDGE, userValues).strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    private static String renderPairs(final List<JudgedPair> pairs) {
        final List<String> blocks = new ArrayList<>();
        for (int index = 0; index < pairs.size(); index++) {
            final JudgedPair pair = pairs.get(index);
            blocks.add("[" + labelOf(index) + "]\nSource: " + pair.maskedSource() + "\nCandidate: "
                    + pair.maskedCandidate());
        }
        return String.join("\n\n", blocks);
    }

    private static List<String> labelsOf(final int count) {
        return IntStream.range(0, count).mapToObj(JudgeCall::labelOf).toList();
    }

    private static String labelOf(final int zeroBasedIndex) {
        return "s" + (zeroBasedIndex + 1);
    }

    private static void logTraceMessages(final ChatRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("Judge messages {}", request.messages());
        }
    }

    private static void logTraceReply(final String reply) {
        if (log.isTraceEnabled()) {
            log.trace("Judge raw reply {}", reply);
        }
    }
}
