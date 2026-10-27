package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The closed "About this book" fold under the verdict line: a one-line summary while closed, the details when opened,
 * and the same word count as the verdict.
 */
class AboutBookFoldTest extends ImportScreenTestBase {

    private static final LanguageEvidence ABSENT =
            BookFixtures.evidence(null, null, null, LanguageEvidence.Verdict.ABSENT);

    @TempDir
    Path dir;

    private void openInspected(
            final String fileName,
            final BookFormat format,
            final @Nullable String title,
            final int chapters,
            final int words,
            final int images,
            final boolean cover)
            throws TimeoutException {
        final Path source = dir.resolve(fileName);
        projects.on(
                source,
                Result.ok(BookFixtures.inspected(
                        "p",
                        format,
                        null,
                        title,
                        null,
                        ABSENT,
                        chapters,
                        words,
                        images,
                        0,
                        cover ? BookFixtures.pngCover() : null)));
        openImport();
        openBook(source);
    }

    private TitledPane fold() {
        return (TitledPane) required("import-about");
    }

    // IF the fold opened by itself, THEN the page would grow past one screen and push the browser down.
    @Test
    void fold_bookOpened_isClosedWithTheOneLineSummary() throws TimeoutException {
        openInspected("book.epub", BookFormat.EPUB, "Frankenstein", 5, 4_149, 3, true);

        assertThat(ThemeTestSupport.onFx(() -> fold().isExpanded())).isFalse();
        assertThat(textOf("import-about-summary"))
                .isEqualTo("EPUB · 5 chapters · about 4,100 words · 3 images · cover");
        assertThat(ThemeTestSupport.onFx(() -> fold().getText())).isEqualTo("About this book");
    }

    // IF the verdict and the fold rounded differently, THEN one page would give two word counts for one book.
    @ParameterizedTest
    @CsvSource({"640, 640 words", "999, 999 words", "1049, about 1,000 words", "78214, about 78,200 words"})
    void verdictAndSummary_sameBook_sayTheSameWordCount(final int words, final String expected)
            throws TimeoutException {
        openInspected("book.epub", BookFormat.EPUB, "Frankenstein", 5, words, 0, false);

        assertThat(textOf("import-verdict-text")).contains(expected);
        assertThat(textOf("import-about-summary")).contains(expected);
    }

    @Test
    void fold_openedOnABookWithNoCover_saysNoCoverInsteadOfABlankBox() throws TimeoutException {
        openInspected("notes.txt", BookFormat.TXT, null, 1, 640, 0, false);

        onFx(() -> fold().setExpanded(true));

        assertThat(textOf("import-cover-none")).isEqualTo("No cover");
        assertThat(optional("import-cover")).isNull();
        assertThat(optional("import-cover-placeholder")).isNull();
    }

    // IF a row were shown for a field the file does not declare, THEN the fold would show a blank as a fact.
    @Test
    void fold_openedOnAFileDeclaringNothing_showsNoTitleAuthorOrLanguageRow() throws TimeoutException {
        openInspected("notes.txt", BookFormat.TXT, null, 1, 640, 0, false);

        onFx(() -> fold().setExpanded(true));

        assertThat(idsStartingWith("import-row-"))
                .doesNotContain("import-row-titleAuthor", "import-row-declaredLang")
                .contains("import-row-file", "import-row-format");
    }

    @Test
    void fold_openedOnABookWithOnlyATitle_showsTheTitleWithoutASeparator() throws TimeoutException {
        openInspected("notes.md", BookFormat.MARKDOWN, "Field Notes", 1, 640, 0, false);

        onFx(() -> fold().setExpanded(true));

        final Label value = ThemeTestSupport.onFx(
                () -> (Label) required("import-row-titleAuthor").lookup(".kv-value"));
        assertThat(value.getText()).isEqualTo("Field Notes");
    }
}
