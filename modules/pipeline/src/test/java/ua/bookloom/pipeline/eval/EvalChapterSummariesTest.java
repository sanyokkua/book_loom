package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.BookBrief;

/** The eval's chapter-summaries switch (arm 6): on and off force the brief's switch, unset leaves the brief alone. */
class EvalChapterSummariesTest {

    private static final BookBrief BRIEF = BookBrief.defaults("en").withLanguages("en", "uk");

    @ParameterizedTest
    @CsvSource({"on,true", "ON,true", " off ,false", "Off,false"})
    void apply_onOrOff_forcesTheSwitch(final String asked, final boolean forced) {
        final BookBrief chosen = EvalChapterSummaries.apply(BRIEF, Map.of(EvalChapterSummaries.ENV, asked));

        assertThat(chosen.chapterSummaries()).isEqualTo(forced);
        assertThat(chosen.summariesOn()).isEqualTo(forced);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void apply_blank_leavesTheBriefToItsStoryShape(final String asked) {
        final BookBrief chosen = EvalChapterSummaries.apply(BRIEF, Map.of(EvalChapterSummaries.ENV, asked));

        assertThat(chosen).isSameAs(BRIEF);
        assertThat(chosen.summariesOn()).isTrue();
    }

    @Test
    void apply_unset_leavesTheBriefToItsStoryShape() {
        assertThat(EvalChapterSummaries.apply(BRIEF, Map.of())).isSameAs(BRIEF);
    }

    @Test
    void apply_unknownValue_refused() {
        final Map<String, String> env = Map.of(EvalChapterSummaries.ENV, "yes");

        assertThatThrownBy(() -> EvalChapterSummaries.apply(BRIEF, env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on or off");
    }
}
