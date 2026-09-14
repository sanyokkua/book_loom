package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code BookFormat}'s case-insensitive filename resolution and suffix preservation.
 */
class BookFormatTest {

    @ParameterizedTest
    @CsvSource({
        "B.FB2.ZIP, FB2, .FB2.ZIP",
        "Book.fb2, FB2, .fb2",
        "b.markdown, MARKDOWN, .markdown",
        "b.md, MARKDOWN, .md",
        "book.epub, EPUB, .epub",
        "book.txt, TXT, .txt"
    })
    void ofFileName_supportedSuffix_returnsFormatAndOriginalSuffix(
            final String fileName, final BookFormat expectedFormat, final String expectedSuffix) {
        assertThat(BookFormat.ofFileName(fileName)).hasValue(expectedFormat);
        assertThat(expectedFormat.matchedSuffix(fileName)).isEqualTo(expectedSuffix);
    }

    @Test
    void suffixes_multiExtensionFormats_areLongestFirst() {
        assertThat(BookFormat.FB2.suffixes()).containsExactly(".fb2.zip", ".fb2");
        assertThat(BookFormat.MARKDOWN.suffixes()).containsExactly(".markdown", ".md");
    }

    @Test
    void ofFileName_unsupportedSuffix_returnsEmpty() {
        assertThat(BookFormat.ofFileName("b.pdf")).isEmpty();
    }

    @Test
    void matchedSuffix_nonMatchingFormat_isRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> BookFormat.EPUB.matchedSuffix("b.pdf"));
    }
}
