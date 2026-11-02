package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The first letter of each sentence is found after the start or a stop mark, and long blank runs stay linear. */
class SentenceStartsTest {

    @Test
    void of_stopMarksQuotesAndDashes_findsEachFirstLetter() {
        final String text = "«Go.» — Then he went. “no” said he?  ok";

        assertThat(SentenceStarts.of(text)).containsExactly(1, 8, 23, 37);
    }

    @Test
    void of_noStopMark_findsOnlyTheStart() {
        assertThat(SentenceStarts.of("  (one) two, three")).containsExactly(3);
        assertThat(SentenceStarts.of("")).isEmpty();
        assertThat(SentenceStarts.of("1. 2.")).isEmpty();
    }

    @Test
    void of_stopMarkWithoutBlankAfterIt_doesNotStartASentence() {
        assertThat(SentenceStarts.of("v1.2 and a.b")).containsExactly(0);
    }

    @Test
    void of_longBlankRunsAfterStopMarks_isLinear() {
        final String text = (". " + " ".repeat(1_000)).repeat(500) + "z";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThat(SentenceStarts.of(text)).hasSize(1));
    }
}
