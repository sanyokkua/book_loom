package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ReviewReplyParser}: tolerant of how a small model wraps its answer, strict about nothing it can ignore. */
class ReviewReplyParserTest {

    private static final ReviewReplyParser PARSER = new ReviewReplyParser(new ObjectMapper());
    private static final List<ReviewedPair> PAIRS = List.of(
            new ReviewedPair("Book.md:0", "One.", "Один."),
            new ReviewedPair("Book.md:1", "Two.", "Два."),
            new ReviewedPair("Book.md:2", "Three.", "Три."));

    @Test
    void parse_editsForTheSecondLabel_readsThemOnTheSecondSegment() {
        final ReviewVerdict verdict = PARSER.parse(
                "{\"results\":[{\"id\":\"s2\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"gender\","
                        + "\"quote\":\"Два\",\"replacement\":\"Дві\"}]}]}",
                PAIRS);

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.items()).singleElement().satisfies(item -> {
            assertThat(item.segmentId()).isEqualTo("Book.md:1");
            assertThat(item.status()).isEqualTo(ReviewStatus.EDITS);
            assertThat(item.edits()).containsExactly(new ReviewEdit(ReviewCriterion.GENDER, "Два", "Дві"));
        });
    }

    @Test
    void parse_okAndRewriteItems_readEachStatus() {
        final ReviewVerdict verdict = PARSER.parse(
                "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s3\",\"status\":\"rewrite\","
                        + "\"rewrite\":\"Тридцять три.\"}]}",
                PAIRS);

        assertThat(verdict.itemFor("Book.md:0")).contains(ReviewItem.ok("Book.md:0"));
        assertThat(verdict.itemFor("Book.md:2"))
                .hasValueSatisfying(item -> assertThat(item.rewrite()).isEqualTo("Тридцять три."));
    }

    @Test
    void parse_replyWrappedInProseAndAFence_isStillRead() {
        final ReviewVerdict verdict =
                PARSER.parse("Here you go:\n```json\n{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"}]}\n```", PAIRS);

        assertThat(verdict.items()).hasSize(1);
    }

    @Test
    void parse_bareArray_isReadAsTheResults() {
        assertThat(PARSER.parse("[{\"id\":\"s1\",\"status\":\"ok\"}]", PAIRS).items())
                .hasSize(1);
    }

    @Test
    void parse_emptyResults_isReadableAndSaysNothingAboutAnySegment() {
        final ReviewVerdict verdict = PARSER.parse("{\"results\":[]}", PAIRS);

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.itemFor("Book.md:0")).isEmpty();
    }

    @Test
    void parse_replyWithNoResultsList_isUnreadable() {
        assertThat(PARSER.parse("{\"score\":0.9}", PAIRS).readable()).isFalse();
    }

    @Test
    void parse_prose_isUnreadable() {
        assertThat(PARSER.parse("Looks good to me!", PAIRS).readable()).isFalse();
    }

    @Test
    void parse_labelOutsideTheBatch_isDropped() {
        assertThat(PARSER.parse("{\"results\":[{\"id\":\"s9\",\"status\":\"ok\"}]}", PAIRS)
                        .items())
                .isEmpty();
    }

    @Test
    void parse_unknownStatus_isDropped() {
        assertThat(PARSER.parse("{\"results\":[{\"id\":\"s1\",\"status\":\"perfect\"}]}", PAIRS)
                        .items())
                .isEmpty();
    }

    @Test
    void parse_statusEditsWithNoUsableEdit_isReadAsOk() {
        final ReviewVerdict verdict = PARSER.parse(
                "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"gender\","
                        + "\"quote\":\"\",\"replacement\":\"x\"},{\"criterion\":\"gender\",\"quote\":\"a\","
                        + "\"replacement\":\"a\"}]}]}",
                PAIRS);

        assertThat(verdict.items()).containsExactly(ReviewItem.ok("Book.md:0"));
    }

    @Test
    void parse_unknownCriterion_isReadAsAStyleNote() {
        final ReviewVerdict verdict = PARSER.parse(
                "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"grammar\","
                        + "\"quote\":\"Один\",\"replacement\":\"Одна\"}]}]}",
                PAIRS);

        assertThat(verdict.items().getFirst().edits().getFirst().criterion()).isEqualTo(ReviewCriterion.STYLE);
    }

    @Test
    void parse_criterionWritten_withUnderscoreAndCapitals_isRecognised() {
        assertThat(ReviewCriterion.of("Invented_Word")).contains(ReviewCriterion.INVENTED_WORD);
    }

    @Test
    void parse_sameSegmentAnsweredTwice_keepsTheFirstAnswer() {
        final ReviewVerdict verdict = PARSER.parse(
                "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},{\"id\":\"s1\",\"status\":\"rewrite\","
                        + "\"rewrite\":\"X\"}]}",
                PAIRS);

        assertThat(verdict.items()).containsExactly(ReviewItem.ok("Book.md:0"));
    }

    @Test
    void parse_rewriteWithNoText_isReadAsOk() {
        assertThat(PARSER.parse("{\"results\":[{\"id\":\"s1\",\"status\":\"rewrite\"}]}", PAIRS)
                        .items())
                .containsExactly(ReviewItem.ok("Book.md:0"));
    }

    private static final String CUT_REPLY = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"},"
            + "{\"id\":\"s2\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"gender\",\"quote\":\"Два\","
            + "\"replacement\":\"Дві\"}]},{\"id\":\"s3\",\"status\":\"edits\",\"edits\":[{\"criterion\":\"gen";

    @Test
    void parseSalvaging_replyCutInsideTheThirdEntry_keepsTheTwoCompleteOnes() {
        final ReviewVerdict verdict = PARSER.parseSalvaging(CUT_REPLY, PAIRS);

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.items()).extracting(ReviewItem::segmentId).containsExactly("Book.md:0", "Book.md:1");
        assertThat(verdict.itemFor("Book.md:1"))
                .hasValueSatisfying(item -> assertThat(item.edits()).hasSize(1));
    }

    @Test
    void parseSalvaging_replyCutBeforeAnyEntryCloses_isReadableAndEmpty() {
        final ReviewVerdict verdict = PARSER.parseSalvaging("{\"results\":[{\"id\":\"s1\",\"status\":\"edi", PAIRS);

        assertThat(verdict.readable()).isTrue();
        assertThat(verdict.items()).isEmpty();
    }

    @Test
    void parse_strictReplyCutMidEntry_staysUnreadable() {
        assertThat(PARSER.parse(CUT_REPLY, PAIRS).readable()).isFalse();
    }
}
