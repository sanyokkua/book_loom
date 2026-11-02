package ua.bookloom.pipeline.narrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Sentence breaks fall after a stop mark and blanks, and at line breaks, and a long run of marks stays linear. */
class SentenceBreaksTest {

    @Test
    void split_marksFollowedByBlanks_cutsThere() {
        assertThat(SentenceBreaks.split("I came. I saw?! I won…  Done"))
                .containsExactly("I came", "I saw", "I won", "Done");
    }

    @Test
    void split_lineBreaksAndMarksWithoutBlanks_cutsOnlyAtLineBreaks() {
        assertThat(SentenceBreaks.split("one\r\ntwo\n\nv1.2 three.")).containsExactly("one", "two", "v1.2 three.");
    }

    @Test
    void split_emptyAndBlankText_givesNothingOrTheBlank() {
        assertThat(SentenceBreaks.split("")).isEqualTo(List.of());
        assertThat(SentenceBreaks.split("a. ")).containsExactly("a");
    }

    @Test
    void split_longRunOfMarksWithoutBlanks_isLinear() {
        final String text = "?".repeat(300_000) + "x";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(SentenceBreaks.split(text)).hasSize(1));
    }
}
