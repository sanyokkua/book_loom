package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentKind;

/**
 * {@code SegmentLocator.of}'s rendered text per kind and position.
 */
class SegmentLocatorTest {

    @ParameterizedTest
    @CsvSource({
        "PARAGRAPH, 5, 12, ch5 · p12",
        "PARAGRAPH, 1, 3, ch1 · p03",
        "PARAGRAPH, 12, 140, ch12 · p140",
        "TITLE, 3, 1, ch3 · p01",
        "METADATA_TITLE, 0, 1, title",
        "METADATA_AUTHOR, 0, 2, author · 02",
        "METADATA_DESCRIPTION, 0, 1, description · 01",
        "NAV_LABEL, 0, 7, nav · 07",
        "TITLE, 0, 12, page title · 12",
        "ALT, 0, 3, alt · 03",
        "FRONTMATTER_VALUE, 0, 1, frontmatter · 01"
    })
    void of_kindAndPosition_rendersExpectedText(
            final SegmentKind kind, final int unitOrdinal, final int segmentOrdinal, final String expected) {
        assertThat(SegmentLocator.of(kind, unitOrdinal, segmentOrdinal).text()).isEqualTo(expected);
    }
}
