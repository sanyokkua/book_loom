package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** {@link ReviewerCall}: the per-chunk reviewer request and how each way a call can end is read. */
class ReviewerCallTest {

    private static final ReviewerCall CALL =
            new ReviewerCall(new PromptTemplates(), new ReviewReplyParser(new ObjectMapper()));
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<ReviewedPair> THREE_PAIRS = List.of(
            new ReviewedPair("Book.md:0", "He opened the ⟦g0⟧old⟦g1⟧ door.", "Він відчинив ⟦g0⟧старі⟦g1⟧ двері."),
            new ReviewedPair("Book.md:1", "She smiled.", "Вона усміхнулася."),
            new ReviewedPair("Book.md:2", "It was quiet.", "Було тихо."));
    private static final String ALL_OK = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"}]}";

    @Test
    void review_threePairs_sendsOneCallAtTemperatureZeroWithAFixedSeedAndTheSchema() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(ALL_OK));

        CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(model.requests()).hasSize(1);
        final ChatRequest request = model.requests().getFirst();
        assertThat(request.temperature()).isEqualTo(0.0);
        assertThat(request.seed()).isEqualTo(15);
        assertThat(request.reasoningEnabled()).isFalse();
        assertThat(request.callKind()).isEqualTo(CallKind.REVIEW);
        assertThat(Objects.requireNonNull(request.responseFormat()).name()).isEqualTo("reviewer");
        assertThat(request.responseFormat().jsonSchema())
                .doesNotContain("maxItems", "maxLength", "additionalProperties")
                .contains("\"enum\":[\"ok\",\"edits\",\"rewrite\"]", "invented-word");
    }

    @Test
    void review_threePairs_labelsThemAndNeverShowsASegmentId() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(ALL_OK));

        CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(model.requests().getFirst().messages().getLast().content())
                .contains(
                        "<Pair id=\"s1\">", "<Pair id=\"s2\">", "<Pair id=\"s3\">", "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.")
                .doesNotContain("Book.md");
    }

    @Test
    void review_glossaryPairsAndTheLanguageChecks_reachThePrompt() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(ALL_OK));

        CALL.review(THREE_PAIRS, FRAME, List.of("Hale → Гейл"), ReviewPass.FIRST, calls(model));

        final ChatRequest request = model.requests().getFirst();
        assertThat(request.messages().getLast().content()).contains("Hale → Гейл");
        assertThat(request.messages().getFirst().content())
                .contains(FRAME.styleSheet().text(), "Check: Quotes are «…» and balanced");
    }

    @Test
    void review_secondPass_carriesItsNarrowerChecklistAndTheFirstDoesNot() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(ALL_OK)).answer(readable(ALL_OK));

        CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));
        CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.SECOND, calls(model));

        assertThat(model.requests().get(0).messages().getLast().content()).doesNotContain("second pass");
        assertThat(model.requests().get(1).messages().getLast().content())
                .contains("second pass", "gender, terminology and agreement");
    }

    @Test
    void review_timeoutThenAnswer_resendsOnceWithoutAResponseFormatAndReadsTheAnswer() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.timeout, "Model failure", "no answer")))
                .answer(readable(ALL_OK));

        final Result<ReviewVerdict> result = CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(0).responseFormat()).isNotNull();
        assertThat(model.requests().get(1).responseFormat()).isNull();
        assertThat(Objects.requireNonNull(result.data()).readable()).isTrue();
    }

    @Test
    void review_twoTimeouts_readsAsUnavailableWithThatError() {
        final AppError timeout = AppError.of(ErrorCode.timeout, "Model failure", "no answer");
        final ScriptedChatModel model = new ScriptedChatModel().answerTimes(2, Result.err(timeout));

        final ReviewVerdict verdict =
                Objects.requireNonNull(CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model))
                        .data());

        assertThat(model.requests()).hasSize(2);
        assertThat(verdict.readable()).isFalse();
        assertThat(verdict.isUnavailable()).isTrue();
        assertThat(Objects.requireNonNull(verdict.unavailableBecause()).code()).isEqualTo(ErrorCode.timeout);
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"emptyCompletion", "contextWindow"})
    void review_modelAnswersEmptyCompletionOrContextWindow_readsAsUnreadable(final ErrorCode code) {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.err(AppError.of(code, "Model failure", "reply failed")));

        final Result<ReviewVerdict> result = CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).readable()).isFalse();
    }

    @Test
    void review_unparsableReply_isUnreadableButNotUnavailable() {
        final ReviewVerdict verdict = Objects.requireNonNull(CALL.review(
                        THREE_PAIRS,
                        FRAME,
                        List.of(),
                        ReviewPass.FIRST,
                        calls(new ScriptedChatModel().answer(readable("Looks good to me!"))))
                .data());

        assertThat(verdict.readable()).isFalse();
        assertThat(verdict.isUnavailable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"auth", "unreachable", "upstream", "rateLimited", "cancelled"})
    void review_modelAnswersAnotherError_returnsThatError(final ErrorCode code) {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(Result.err(AppError.of(code, "Model failure", "no answer")));

        final Result<ReviewVerdict> result = CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(code);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void followedBy_secondPassEdits_followTheFirstPassEditsOfTheSameSegment() {
        final ReviewVerdict first = ReviewVerdict.answered(List.of(new ReviewItem(
                "a", ReviewStatus.EDITS, List.of(new ReviewEdit(ReviewCriterion.GENDER, "x", "y")), null)));
        final ReviewVerdict second = ReviewVerdict.answered(List.of(new ReviewItem(
                "a", ReviewStatus.EDITS, List.of(new ReviewEdit(ReviewCriterion.AGREEMENT, "p", "q")), null)));

        assertThat(first.followedBy(second).itemFor("a"))
                .hasValueSatisfying(item -> assertThat(item.edits())
                        .extracting(ReviewEdit::criterion)
                        .containsExactly(ReviewCriterion.GENDER, ReviewCriterion.AGREEMENT));
    }

    @Test
    void followedBy_secondPassUnreadable_keepsTheFirstPassAnswers() {
        final ReviewVerdict first = ReviewVerdict.answered(List.of(ReviewItem.ok("a")));

        assertThat(first.followedBy(ReviewVerdict.unreadable())).isEqualTo(first);
    }

    @Test
    void followedBy_aRewriteInEitherPass_winsOverEdits() {
        final ReviewVerdict first = ReviewVerdict.answered(List.of(ReviewItem.ok("a")));
        final ReviewVerdict second =
                ReviewVerdict.answered(List.of(new ReviewItem("a", ReviewStatus.REWRITE, List.of(), "Нове речення.")));

        assertThat(first.followedBy(second).itemFor("a"))
                .hasValueSatisfying(item -> assertThat(item.rewrite()).isEqualTo("Нове речення."));
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    @Test
    void review_characterSheet_reachesThePromptOnlyWhenThereIsOne() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(ALL_OK)).answer(readable(ALL_OK));

        CALL.review(THREE_PAIRS, FRAME, List.of(), List.of("Lyra — female"), ReviewPass.FIRST, calls(model));
        CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(model.requests().get(0).messages().getLast().content())
                .contains("[Characters in these pairs", "Lyra — female");
        assertThat(model.requests().get(1).messages().getLast().content()).doesNotContain("Characters in these pairs");
    }

    private static List<ReviewedPair> eightPairs() {
        return IntStream.range(0, 8)
                .mapToObj(index -> new ReviewedPair("Book.md:" + index, "Source " + index, "Кандидат " + index))
                .toList();
    }

    private static String okEntry(final int label) {
        return "{\"id\":\"s" + label + "\",\"status\":\"ok\"}";
    }

    private static Result<ChatResponse> cut(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.LENGTH));
    }

    private static String fiveThenACutSixth() {
        return "{\"results\":["
                + IntStream.rangeClosed(1, 5)
                        .mapToObj(ReviewerCallTest::okEntry)
                        .collect(java.util.stream.Collectors.joining(","))
                + ",{\"id\":\"s6\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"gen";
    }

    @Test
    void review_replyCutAfterFiveOfEight_keepsFiveAndAsksOnceForTheThreeUnread() {
        final String rest = "{\"results\":[" + okEntry(1) + "," + okEntry(2)
                + ",{\"id\":\"s3\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"gender\","
                + "\"quote\":\"Кандидат 7\",\"replacement\":\"Кандидатка 7\"}]}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(cut(fiveThenACutSixth())).answer(readable(rest));

        final ReviewVerdict verdict =
                Objects.requireNonNull(CALL.review(eightPairs(), FRAME, List.of(), ReviewPass.FIRST, calls(model))
                        .data());

        assertThat(model.requests()).hasSize(2);
        final String asked = model.requests().get(1).messages().getLast().content();
        assertThat(asked).contains("Кандидат 5", "Кандидат 6", "Кандидат 7").doesNotContain("Кандидат 4");
        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.isUnavailable()).isFalse();
        assertThat(verdict.items()).hasSize(8);
        assertThat(verdict.itemFor("Book.md:7"))
                .hasValueSatisfying(item -> assertThat(item.status()).isEqualTo(ReviewStatus.EDITS));
    }

    @Test
    void review_replyCutAndTheReAskCutToo_neverReadsAsUnavailableAndAsksOnlyOnce() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(cut(fiveThenACutSixth()))
                .answer(cut("{\"results\":[{\"id\":\"s1\",\"sta"));

        final ReviewVerdict verdict =
                Objects.requireNonNull(CALL.review(eightPairs(), FRAME, List.of(), ReviewPass.FIRST, calls(model))
                        .data());

        assertThat(model.requests()).hasSize(2);
        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.isUnavailable()).isFalse();
        assertThat(verdict.items()).hasSize(5);
        assertThat(verdict.itemFor("Book.md:6")).isEmpty();
    }

    @Test
    void review_replyCutBeforeAnyEntry_isNotUnreadable() {
        final ScriptedChatModel model = new ScriptedChatModel().answerTimes(2, cut("{\"results\":[{\"id\":\"s1"));

        final ReviewVerdict verdict =
                Objects.requireNonNull(CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model))
                        .data());

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.isUnavailable()).isFalse();
    }

    @Test
    void review_reAskFailsWithAnError_keepsTheSalvagedAnswers() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(cut(fiveThenACutSixth()))
                .answer(Result.err(AppError.of(ErrorCode.upstream, "Model failure", "down")));

        final Result<ReviewVerdict> result =
                CALL.review(eightPairs(), FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).items()).hasSize(5);
    }

    @Test
    void review_completeReplyThatLeavesSomeIdsOut_isNotAskedAgain() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(readable(ALL_OK));

        CALL.review(THREE_PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls(model));

        assertThat(model.requests()).hasSize(1);
    }
}
