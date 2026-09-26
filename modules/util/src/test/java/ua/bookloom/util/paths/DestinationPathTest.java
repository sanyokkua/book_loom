package ua.bookloom.util.paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;

/** {@code DestinationPath}: where the translated book is written relative to its source. */
class DestinationPathTest {

    // the language is inserted before a plain single-suffix extension, in the source's own directory.
    @Test
    void destinationFor_plainEpub_insertsLanguageBeforeSuffix() {
        final Path destination =
                DestinationPath.destinationFor(Path.of("/books/Frankenstein.epub"), BookFormat.EPUB, "uk");

        assertThat(destination).isEqualTo(Path.of("/books/Frankenstein.uk.epub"));
    }

    // the composite .fb2.zip suffix stays whole: the language goes before it, never between .fb2 and .zip.
    @Test
    void destinationFor_compoundFb2Zip_keepsTheCompoundSuffixTogether() {
        final Path destination = DestinationPath.destinationFor(Path.of("/books/Kobzar.fb2.zip"), BookFormat.FB2, "uk");

        assertThat(destination)
                .isEqualTo(Path.of("/books/Kobzar.uk.fb2.zip"))
                .isNotEqualTo(Path.of("/books/Kobzar.fb2.uk.zip"));
    }

    // a source with no parent directory yields a bare relative file name, not a path rooted anywhere.
    @Test
    void destinationFor_sourceWithoutParent_returnsBareFileName() {
        final Path destination = DestinationPath.destinationFor(Path.of("Book.md"), BookFormat.MARKDOWN, "uk");

        assertThat(destination).isEqualTo(Path.of("Book.uk.md"));
    }

    // every other supported suffix, and an upper-case one whose original casing must survive.
    @ParameterizedTest
    @CsvSource({
        "/books/Poem.fb2, FB2, uk, /books/Poem.uk.fb2",
        "/books/Notes.markdown, MARKDOWN, en, /books/Notes.en.markdown",
        "/books/Notes.txt, TXT, pl, /books/Notes.pl.txt",
        "/books/BOOK.EPUB, EPUB, de, /books/BOOK.de.EPUB"
    })
    void destinationFor_supportedSuffix_insertsLanguageBeforeItKeepingCase(
            final String source, final BookFormat format, final String language, final String expected) {
        final Path destination = DestinationPath.destinationFor(Path.of(source), format, language);

        assertThat(destination).isEqualTo(Path.of(expected));
    }

    // requireNonNull guards the source argument.
    @SuppressWarnings("NullAway")
    @Test
    void destinationFor_nullSource_throwsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> DestinationPath.destinationFor(null, BookFormat.EPUB, "uk"))
                .withMessageContaining("source");
    }

    // requireNonNull guards the format argument.
    @SuppressWarnings("NullAway")
    @Test
    void destinationFor_nullFormat_throwsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> DestinationPath.destinationFor(Path.of("/books/A.epub"), null, "uk"))
                .withMessageContaining("format");
    }

    // requireNonNull guards the target-language argument.
    @SuppressWarnings("NullAway")
    @Test
    void destinationFor_nullTargetLanguage_throwsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> DestinationPath.destinationFor(Path.of("/books/A.epub"), BookFormat.EPUB, null))
                .withMessageContaining("targetLanguage");
    }
}
