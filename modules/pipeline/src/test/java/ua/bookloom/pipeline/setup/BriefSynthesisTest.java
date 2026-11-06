package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.BriefSuggestion.ShapeQuote;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.StoryShape;

/**
 * The story shape the brief's synthesis answers (design D6): one quote from each of two sample windows, each held to
 * the window it names, so the shape stands on two verified passages; with fewer it is only likely, and with no answer
 * there is no shape. Every sentence of the test book opens with {@link #QUOTE}, so the quote is in every window and
 * a line the book never had is in none.
 */
class BriefSynthesisTest extends SetupAssistantBriefTestBase {

    private static final String MISSING = "A line this book never had";

    private static String synthesisWithShape(final String quotes, final String value) {
        return SYNTHESIS.substring(0, SYNTHESIS.length() - 1) + ",\"storyShape\":{\"quotes\":[" + quotes
                + "],\"value\":\"" + value + "\"}}";
    }

    private static String quote(final int passage, final String text) {
        return "{\"passage\":" + passage + ",\"quote\":\"" + text + "\"}";
    }

    private BriefSuggestion suggestedWith(final String synthesis) {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        synthesisAnswers(synthesis);
        return suggested();
    }

    @Test
    void parse_storyShapeWithTwoQuotes_likely() {
        final BriefSuggestion suggestion =
                suggestedWith(synthesisWithShape(quote(1, QUOTE) + "," + quote(3, QUOTE), "one story"));

        assertThat(suggestion.storyShape()).isEqualTo(StoryShape.ONE_STORY);
        assertThat(suggestion.storyShapeQuotes()).containsExactly(new ShapeQuote(1, QUOTE), new ShapeQuote(3, QUOTE));
        assertThat(suggestion.evidenceOf(BriefField.STORY_SHAPE))
                .contains(new FieldEvidence(Basis.PASSAGES, java.util.List.of(QUOTE, QUOTE), 2, 2));
        assertThat(suggestion.likelyFields()).contains(ua.bookloom.api.project.BriefField.STORY_SHAPE);
    }

    @Test
    void parse_separateStories_isTheShape() {
        final BriefSuggestion suggestion =
                suggestedWith(synthesisWithShape(quote(1, QUOTE) + "," + quote(2, QUOTE), "separate stories"));

        assertThat(suggestion.storyShape()).isEqualTo(StoryShape.SEPARATE_STORIES);
        assertThat(suggestion.evidenceOf(BriefField.STORY_SHAPE))
                .hasValueSatisfying(proof -> assertThat(proof.basis()).isEqualTo(Basis.PASSAGES));
    }

    // IF a quote the window lacks counted, THEN a model could invent the two passages a shape stands on.
    @Test
    void parse_quoteNotInWindow_likelyNoQuotes() {
        final BriefSuggestion suggestion =
                suggestedWith(synthesisWithShape(quote(1, QUOTE) + "," + quote(3, MISSING), "one story"));

        assertThat(suggestion.storyShape()).isEqualTo(StoryShape.ONE_STORY);
        assertThat(suggestion.storyShapeQuotes()).isEmpty();
        assertThat(suggestion.evidenceOf(BriefField.STORY_SHAPE))
                .contains(new FieldEvidence(Basis.LIKELY, java.util.List.of(), 1, 2));
        assertThat(suggestion.likelyFields()).contains(ua.bookloom.api.project.BriefField.STORY_SHAPE);
    }

    // IF one passage cited twice counted twice, THEN a shape would stand on one window, never compared with another.
    @Test
    void parse_samePassageTwice_countsOnce() {
        final BriefSuggestion suggestion =
                suggestedWith(synthesisWithShape(quote(2, QUOTE) + "," + quote(2, QUOTE), "one story"));

        assertThat(suggestion.evidenceOf(BriefField.STORY_SHAPE))
                .contains(new FieldEvidence(Basis.LIKELY, java.util.List.of(), 1, 2));
    }

    @Test
    void parse_passageOutOfRange_isNoSupport() {
        final BriefSuggestion suggestion =
                suggestedWith(synthesisWithShape(quote(1, QUOTE) + "," + quote(WINDOWS + 1, QUOTE), "one story"));

        assertThat(suggestion.evidenceOf(BriefField.STORY_SHAPE))
                .hasValueSatisfying(proof -> assertThat(proof.basis()).isEqualTo(Basis.LIKELY));
    }

    @Test
    void parse_noStoryShape_null() {
        final BriefSuggestion suggestion = suggestedWith(SYNTHESIS);

        assertThat(suggestion.storyShape()).isNull();
        assertThat(suggestion.storyShapeQuotes()).isEmpty();
        assertThat(suggestion.evidenceOf(BriefField.STORY_SHAPE)).isEmpty();
        assertThat(suggestion.likelyFields()).doesNotContain(ua.bookloom.api.project.BriefField.STORY_SHAPE);
    }

    @Test
    void parse_unknownShapeWord_null() {
        final BriefSuggestion suggestion =
                suggestedWith(synthesisWithShape(quote(1, QUOTE) + "," + quote(3, QUOTE), "a trilogy"));

        assertThat(suggestion.storyShape()).isNull();
        assertThat(suggestion.evidenceOf(BriefField.STORY_SHAPE)).isEmpty();
    }

    // The synthesis sees only reports, so each report carries its passage's opening for the shape's quotes.
    @Test
    void synthesisInput_eachReport_holdsItsPassagesOpening() {
        suggestedWith(SYNTHESIS);

        assertThat(synthesisUserMessage())
                .contains("Passage 1: opening \"" + QUOTE, "Passage " + WINDOWS + ": opening \"" + QUOTE);
    }
}
