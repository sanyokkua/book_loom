package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The import card's format row names the format once, whether the inspection's version names it or not. */
class ImportCardFormatTest {

    // IF the name were put in front of a version that already carries it, THEN the row would read "EPUB EPUB 2.0".
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            nullValues = "null",
            value = {
                "EPUB | EPUB 2.0 | EPUB 2.0",
                "EPUB | epub 3.0 | epub 3.0",
                "EPUB | 3.0 | EPUB 3.0",
                "FB2 | null | FB2",
                "TXT | ' ' | TXT"
            })
    void formatText_versionWithOrWithoutTheName_namesTheFormatOnce(
            final String format, final @Nullable String version, final String expected) {
        assertThat(ImportCardView.formatText(format, version)).isEqualTo(expected);
    }
}
