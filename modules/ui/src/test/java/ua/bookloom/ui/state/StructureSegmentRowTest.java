package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.SegmentPreview;

/** The filter a person's search text and kind choice apply to one row of the segment list. */
class StructureSegmentRowTest {

    private static StructureSegmentRow row() {
        return new StructureSegmentRow(
                new SegmentPreview(
                        "s1",
                        "ch2 · p07",
                        SegmentKind.HEADING,
                        "The Storm Breaks",
                        "The Storm Breaks",
                        4,
                        false,
                        false,
                        1,
                        1,
                        false),
                true);
    }

    // IF the search were case sensitive or ignored the locator, THEN a person could not find a passage by what they
    // remember of it or by the name the review desk gives it.
    @ParameterizedTest
    @CsvSource(
            value = {"storm,true", "STORM BREAKS,true", "p07,true", "CH2,true", "calm,false", "'  ',true", "'',true"})
    void matches_searchText_isACaseInsensitiveContainsOnTextOrLocator(final String needle, final boolean expected) {
        assertThat(row().matches(needle, null)).isEqualTo(expected);
    }

    // IF the kind filter let other kinds through, THEN looking at headings alone would show paragraphs too.
    @Test
    void matches_kindChosen_keepsOnlyThatKind() {
        assertThat(row().matches("", SegmentKind.HEADING)).isTrue();
        assertThat(row().matches("", SegmentKind.PARAGRAPH)).isFalse();
        assertThat(row().matches("storm", SegmentKind.PARAGRAPH)).isFalse();
    }
}
