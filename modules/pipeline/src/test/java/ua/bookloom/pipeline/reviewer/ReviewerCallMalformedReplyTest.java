package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * A reviewer reply that ended normally (finish=stop) but is malformed JSON is salvaged and re-asked like a cut one,
 * never flagged for the whole batch: the shapes below are the slips a 26b model made in a real run.
 */
class ReviewerCallMalformedReplyTest {

    private static final ReviewerCall CALL =
            new ReviewerCall(new PromptTemplates(), new ReviewReplyParser(new ObjectMapper()));
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<ReviewedPair> PAIRS = List.of(
            new ReviewedPair("Book.md:0", "One.", "Один."),
            new ReviewedPair("Book.md:1", "Two.", "Два."),
            new ReviewedPair("Book.md:2", "Three.", "Три."));
    private static final String EDIT_TAIL = "\"quote\":\"Два.\",\"replacement\":\"Двоє.\"}]}";
    private static final String GOOD_SECOND_ANSWER =
            "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"style\"," + EDIT_TAIL + "]}";

    @Test
    void review_keyMissingItsOpeningQuote_isRepairedWithoutASecondCall() {
        final String reply = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s2\",\"status\":\"edits\","
                + "\"edits\":[{\"criterion\":\"style\",\"quote\":\"Два.\",replacement\":\"Двоє.\"}]},"
                + "{\"id\":\"s3\",\"status\":\"ok\"}]}";
        final ScriptedChatModel model = new ScriptedChatModel().answer(stopped(reply));

        final ReviewVerdict verdict = review(model);

        assertThat(model.requests()).hasSize(1);
        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.items()).hasSize(3);
        assertThat(verdict.itemFor("Book.md:1"))
                .hasValueSatisfying(item -> assertThat(item.edits())
                        .extracting(ReviewEdit::replacement)
                        .containsExactly("Двоє."));
    }

    @Test
    void review_structureSlipInTheSecondEntry_keepsTheFirstAndAsksOnceForTheRest() {
        final String reply = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},"
                + "{\"id\":\"s2\",\"status\":\"edits\":[{\"criterion\":\"style\",\"quote\":\"Два.\"}]}]}";
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(stopped(reply))
                .answer(stopped("{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s2\",\"status\":\"ok\"}]}"));

        final ReviewVerdict verdict = review(model);

        assertThat(model.requests()).hasSize(2);
        final String asked = model.requests().get(1).messages().getLast().content();
        assertThat(asked).contains("Два.", "Три.").doesNotContain("Один.");
        assertThat(model.requests().get(1).seed()).isNotEqualTo(ReviewerCall.REVIEWER_SEED);
        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.isUnavailable()).isFalse();
        assertThat(verdict.items()).hasSize(3);
    }

    @Test
    void review_trailingJunkInsideAnEntry_isReaskedAndAnswerIsUsed() {
        final String reply = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"style\","
                + "\"quote\":\"Один.\",\"replacement\":\"Одне.\",\"}]}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(stopped(reply)).answer(stopped(GOOD_SECOND_ANSWER));

        final ReviewVerdict verdict = review(model);

        assertThat(model.requests()).hasSize(2);
        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.itemFor("Book.md:0"))
                .hasValueSatisfying(item -> assertThat(item.edits())
                        .extracting(ReviewEdit::replacement)
                        .containsExactly("Двоє."));
    }

    @Test
    void review_stillMalformedAfterTheReAsk_isTakenAsOkAndNeverFlagged() {
        final String broken = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\":[";
        final ScriptedChatModel model = new ScriptedChatModel().answerTimes(2, stopped(broken));

        final ReviewVerdict verdict = review(model);

        assertThat(model.requests()).hasSize(2);
        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.isUnavailable()).isFalse();
        assertThat(verdict.items()).isEmpty();
    }

    private static ReviewVerdict review(final ScriptedChatModel model) {
        final ModelCalls calls = (kind, segmentId, request) -> model.chat(request);
        return Objects.requireNonNull(
                CALL.review(PAIRS, FRAME, List.of(), ReviewPass.FIRST, calls).data());
    }

    private static Result<ChatResponse> stopped(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
