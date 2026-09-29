package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/** The summary call is shown a long chapter's beginning and end, never more than its context can hold. */
class ChapterTextTest {

    @Test
    void capped_linesThatFit_areJoinedUnchanged() {
        assertThat(ChapterText.capped(List.of("One.", "Two."), "en", 100)).isEqualTo("One.\nTwo.");
    }

    @Test
    void capped_longChapter_keepsItsBeginningAndEndWithinTheCap() {
        final List<String> lines = IntStream.rangeClosed(1, 400)
                .mapToObj(line -> "Line " + line + " of the chapter.")
                .toList();

        final String capped = ChapterText.capped(lines, "en", 200);

        assertThat(TokenEstimator.estimate(capped, "en"))
                .isLessThanOrEqualTo(200)
                .isGreaterThan(190);
        assertThat(capped)
                .startsWith("Line 1 of the chapter.\nLine 2 of")
                .endsWith("Line 399 of the chapter.\nLine 400 of the chapter.")
                .contains(ChapterText.GAP)
                .doesNotContain("Line 200 of");
    }
}
