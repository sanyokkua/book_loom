package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code ChapterSummary} keeps its parts and refuses a record no chapter could have.
 */
class ChapterSummaryTest {

    @Test
    void constructor_validParts_areKept() {
        final ChapterSummary summary = new ChapterSummary("p1", "ch1.xhtml:0", "ch1.xhtml", 0, 12, "Двоє йдуть.", "h1");

        assertThat(summary.chapterKey()).isEqualTo("ch1.xhtml:0");
        assertThat(summary.segmentCount()).isEqualTo(12);
        assertThat(summary.text()).isEqualTo("Двоє йдуть.");
    }

    @ParameterizedTest
    @CsvSource({"-1, 3, text, h1", "0, 0, text, h1", "0, 3, ' ', h1", "0, 3, text, ' '"})
    void constructor_impossiblePart_throws(
            final int firstSegment, final int segmentCount, final String text, final String sourceHash) {
        assertThatThrownBy(() -> new ChapterSummary(
                        "p1", "ch1.xhtml:0", "ch1.xhtml", firstSegment, segmentCount, text, sourceHash))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
