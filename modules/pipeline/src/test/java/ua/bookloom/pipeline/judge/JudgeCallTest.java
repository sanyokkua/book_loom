package ua.bookloom.pipeline.judge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** {@link JudgeCall}: the per-chunk judge request and its tolerant reply reading. */
class JudgeCallTest {

    private static final JudgeCall CALL =
            new JudgeCall(new PromptTemplates(), new JudgeReplyParser(new ObjectMapper()));
    private static final String SEGMENT_ID_1 = "Book.md:0";
    private static final String SEGMENT_ID_2 = "Book.md:1";
    private static final String SEGMENT_ID_3 = "Book.md:2";
    private static final String SOURCE_PREFIX = "Book.md:";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<JudgedPair> THREE_PAIRS = List.of(
            new JudgedPair(SEGMENT_ID_1, "He opened the ⟦g0⟧old⟦g1⟧ door.", "Він відчинив ⟦g0⟧старі⟦g1⟧ двері."),
            new JudgedPair(SEGMENT_ID_2, "She smiled.", "Вона усміхнулася."),
            new JudgedPair(SEGMENT_ID_3, "It was quiet.", "Було тихо."));
    private static final String FINDING_ON_SECOND_LABEL = "{\"score\":0.82,\"verdict\":\"accept\","
            + "\"findings\":[{\"segmentId\":\"s2\",\"type\":\"omission\",\"severity\":\"medium\","
            + "\"note\":\"drops the second clause\"}],\"deferrals\":[]}";
    private static final String NO_FINDINGS_OR_DEFERRALS = "{\"score\":0.91,\"verdict\":\"accept\"}";
    private static final String UNPARSABLE_REPLY = "Looks good to me!";
    private static final String SCORE_ABOVE_ONE = "{\"score\":1.4}";
    private static final String VERDICT_WITH_NO_SCORE = "{\"verdict\":\"accept\"}";
    private static final String FINDING_ON_LABEL_OUTSIDE_CHUNK =
            "{\"score\":0.9,\"findings\":[{\"segmentId\":\"s9\",\"type\":\"meaning\",\"severity\":\"high\","
                    + "\"note\":\"n/a\"}]}";
    private static final String FINDING_WITH_UNKNOWN_SEVERITY =
            "{\"score\":0.9,\"findings\":[{\"segmentId\":\"s1\",\"type\":\"meaning\",\"severity\":\"critical\","
                    + "\"note\":\"n/a\"}]}";

    @Test
    void judge_threePairs_sendsOneCallAtJudgeTemperatureWithLabelsStyleSheetAndNoExpectedOutput() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(NO_FINDINGS_OR_DEFERRALS));

        CALL.judge(THREE_PAIRS, FRAME, List.of(), calls(model));

        assertThat(model.requests()).hasSize(1);
        final ChatRequest request = model.requests().getFirst();
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.expectedOutputTokens()).isNull();
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().getFirst().content())
                .contains(FRAME.styleSheet().text());
        assertThat(request.messages().getLast().content())
                .contains("s1", "s2", "s3")
                .doesNotContain(SOURCE_PREFIX);
    }

    @Test
    void judge_replyWithFindingOnSecondLabel_readsScoreAndOneFindingOnItsSegmentId() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(FINDING_ON_SECOND_LABEL));

        final JudgeVerdict verdict = verdictOf(model);

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.score()).isEqualTo(0.82);
        assertThat(verdict.deferrals()).isEmpty();
        assertThat(verdict.findings()).hasSize(1);
        final JudgeFinding finding = verdict.findings().getFirst();
        assertThat(finding.segmentId()).isEqualTo(SEGMENT_ID_2);
        assertThat(finding.type()).isEqualTo("omission");
        assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(finding.note()).isEqualTo("drops the second clause");
    }

    @Test
    void judge_replyWithNoFindingsOrDeferralsFields_readsScoreWithEmptyLists() {
        final JudgeVerdict verdict = verdictOf(new ScriptedChatModel().answer(readable(NO_FINDINGS_OR_DEFERRALS)));

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.score()).isEqualTo(0.91);
        assertThat(verdict.findings()).isEmpty();
        assertThat(verdict.deferrals()).isEmpty();
    }

    @Test
    void judge_unparsableReply_readsAsUnreadable() {
        final JudgeVerdict verdict = verdictOf(new ScriptedChatModel().answer(readable(UNPARSABLE_REPLY)));

        assertThat(verdict.readable()).isFalse();
    }

    @Test
    void judge_scoreAboveOne_readsAsUnreadable() {
        final JudgeVerdict verdict = verdictOf(new ScriptedChatModel().answer(readable(SCORE_ABOVE_ONE)));

        assertThat(verdict.readable()).isFalse();
    }

    @Test
    void judge_verdictWithNoScore_readsAsUnreadable() {
        final JudgeVerdict verdict = verdictOf(new ScriptedChatModel().answer(readable(VERDICT_WITH_NO_SCORE)));

        assertThat(verdict.readable()).isFalse();
    }

    @Test
    void judge_findingForLabelOutsideChunk_isDropped() {
        final JudgeVerdict verdict =
                verdictOf(new ScriptedChatModel().answer(readable(FINDING_ON_LABEL_OUTSIDE_CHUNK)));

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.findings()).isEmpty();
    }

    @Test
    void judge_findingWithUnknownSeverity_isDropped() {
        final JudgeVerdict verdict = verdictOf(new ScriptedChatModel().answer(readable(FINDING_WITH_UNKNOWN_SEVERITY)));

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.findings()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"emptyCompletion", "contextWindow"})
    void judge_modelAnswersEmptyCompletionOrContextWindow_readsAsUnreadable(final ErrorCode code) {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.err(AppError.of(code, "Model failure", "reply failed")));

        final Result<JudgeVerdict> result = CALL.judge(THREE_PAIRS, FRAME, List.of(), calls(model));

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).readable()).isFalse();
    }

    @Test
    void judge_modelAnswersUnreachable_returnsThatError() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Unreachable", "no route to host")));

        final Result<JudgeVerdict> result = CALL.judge(THREE_PAIRS, FRAME, List.of(), calls(model));

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.unreachable);
    }

    private static JudgeVerdict verdictOf(final ScriptedChatModel model) {
        final Result<JudgeVerdict> result = CALL.judge(THREE_PAIRS, FRAME, List.of(), calls(model));
        return Objects.requireNonNull(result.data());
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
