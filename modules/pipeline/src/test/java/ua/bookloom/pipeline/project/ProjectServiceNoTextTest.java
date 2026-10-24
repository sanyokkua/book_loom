package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.pipeline.TestBooks;

/** A book whose body holds no word is refused at import with its own verdict, and no project is made for it. */
class ProjectServiceNoTextTest {

    @TempDir
    private Path tempDir;

    private final ProjectWorld world = new ProjectWorld();

    private ImportedBook imported(final Path book) {
        final Result<ImportedBook> result = world.service.create(book);
        assertThat(result.isOk()).as("create result %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data());
    }

    private void assertRefusedAsNoText(final ImportedBook book, final BookFormat format) {
        assertThat(book.projectId()).isNull();
        assertThat(book.profile()).isNull();
        assertThat(book.inspection().verdict()).isEqualTo(InspectionVerdict.NO_TEXT);
        assertThat(book.inspection().format()).isEqualTo(format);
        assertThat(world.storedProjects()).isEmpty();
    }

    // IF an empty or whitespace-only text file became a project, THEN the person would set up a translation of nothing.
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\n\t \n"})
    void create_textFileWithNoWords_refusedAsNoTextWithNoProject(final String content) {
        assertRefusedAsNoText(imported(TestBooks.txt(tempDir.resolve("empty.txt"), content)), BookFormat.TXT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  \n\n  "})
    void create_markdownWithNoWords_refusedAsNoTextWithNoProject(final String content) {
        assertRefusedAsNoText(imported(TestBooks.markdown(tempDir.resolve("empty.md"), content)), BookFormat.MARKDOWN);
    }

    @Test
    void create_fb2WithNoParagraph_refusedAsNoTextWithNoProject() {
        assertRefusedAsNoText(imported(TestBooks.fb2(tempDir.resolve("empty.fb2"), List.of(), "en")), BookFormat.FB2);
    }

    @Test
    void create_epubWithAnEmptyBody_refusedAsNoTextWithNoProject() {
        assertRefusedAsNoText(
                imported(TestBooks.epub(tempDir.resolve("empty.epub"), List.of(List.of()), "en")), BookFormat.EPUB);
    }

    // IF a refused empty book stayed open, THEN importing it again would find a stale document.
    @Test
    void create_emptyBookImportedTwice_refusedBothTimes() {
        final Path book = TestBooks.txt(tempDir.resolve("empty.txt"), "");
        imported(book);

        assertRefusedAsNoText(imported(book), BookFormat.TXT);
    }

    // IF the refusal reached a book with words, THEN a real book would be lost to it.
    @Test
    void create_bookWithOneWord_isStored() {
        final ImportedBook book = imported(TestBooks.txt(tempDir.resolve("one.txt"), "Hello."));

        assertThat(book.projectId()).isNotNull();
        assertThat(book.inspection().verdict()).isEqualTo(InspectionVerdict.READABLE);
        assertThat(world.storedProjects()).hasSize(1);
    }
}
