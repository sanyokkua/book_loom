package ua.bookloom.pipeline.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.pipeline.OriginalTitle;
import ua.bookloom.api.pipeline.TitleOrigin;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.TestBooks;

/** The original title is taken from the first source that has one, and says which source it was. */
class OriginalTitlesTest {

    private static final String LONG_TEXT = "The rain had not stopped for three days, and the city was grey.";

    @TempDir
    private Path dir;

    private final DocumentPort documents =
            Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);

    private OriginalTitle titleOf(final Path book, final BookFormat format) {
        final Document document = Objects.requireNonNull(documents.open(book).data(), "document");
        final Project project = new Project(
                "p1",
                book,
                format,
                document.contentHash(),
                BookBrief.defaults("en"),
                "Book",
                NameOrigin.AUTO,
                "Book",
                Instant.EPOCH,
                Instant.EPOCH);
        return OriginalTitles.of(document, project);
    }

    private Path write(final String fileName, final String text) throws IOException {
        return Files.writeString(dir.resolve(fileName), text, StandardCharsets.UTF_8);
    }

    // IF the book's own details were not first, THEN a well-titled EPUB would be named by something less certain.
    @Test
    void of_epubWithDetailsTitle_isTheDetailsTitle() {
        final Path book = TestBooks.epubWithBookTitle(
                dir.resolve("earth-gravity.epub"), List.of(List.of("Prologue", LONG_TEXT)), "Earth Gravity");

        final OriginalTitle title = titleOf(book, BookFormat.EPUB);

        assertThat(title).isEqualTo(new OriginalTitle("Earth Gravity", TitleOrigin.DETAILS, "aux:title"));
    }

    // IF the front matter did not beat the first heading, THEN a Markdown book would show a chapter name as its title.
    @Test
    void of_markdownWithFrontMatterTitleAndHeading_isTheFrontMatterTitle() throws IOException {
        final Path book = write("notes.md", "---\ntitle: Field Notes\n---\n\n# Chapter One\n\n" + LONG_TEXT + "\n");

        final OriginalTitle title = titleOf(book, BookFormat.MARKDOWN);

        assertThat(title).isEqualTo(new OriginalTitle("Field Notes", TitleOrigin.FRONT_MATTER, "aux:fm:title"));
    }

    // IF a Markdown book's front-matter title counted as the book's details, THEN the reason line would be wrong.
    @Test
    void of_markdownWithFrontMatterTitle_neverSaysDetails() throws IOException {
        final Path book = write("notes.md", "---\ntitle: Field Notes\n---\n\n" + LONG_TEXT + "\n");

        assertThat(titleOf(book, BookFormat.MARKDOWN).origin()).isNotEqualTo(TitleOrigin.DETAILS);
    }

    // IF the first heading were not used, THEN a Markdown book with no front matter would fall to its file name.
    @Test
    void of_markdownWithoutFrontMatterTitle_isTheFirstHeading() throws IOException {
        final Path book = write("notes.md", "# Field Notes\n\n" + LONG_TEXT + "\n\n## Later\n\nMore.\n");

        final OriginalTitle title = titleOf(book, BookFormat.MARKDOWN);

        assertThat(title.text()).isEqualTo("Field Notes");
        assertThat(title.origin()).isEqualTo(TitleOrigin.FIRST_HEADING);
        assertThat(title.segmentId()).isNotNull();
    }

    // IF the front matter has other keys but no title, THEN the heading is still what names the book.
    @Test
    void of_markdownFrontMatterWithoutTitleAndAHeading_isTheFirstHeading() throws IOException {
        final Path book = write("notes.md", "---\nlang: en\n---\n\n# Field Notes\n\n" + LONG_TEXT + "\n");

        assertThat(titleOf(book, BookFormat.MARKDOWN).origin()).isEqualTo(TitleOrigin.FIRST_HEADING);
    }

    // IF a plain-text book had no title, THEN the file name without its suffix is the title and no segment holds it.
    @Test
    void of_plainTextBook_isTheFileNameWithoutItsSuffix() throws IOException {
        final Path book = write("earth-gravity.txt", LONG_TEXT + "\n");

        final OriginalTitle title = titleOf(book, BookFormat.TXT);

        assertThat(title).isEqualTo(new OriginalTitle("earth-gravity", TitleOrigin.FILE_NAME, null));
    }

    // IF only the last dot were cut, THEN a two-part suffix would be left half in the title.
    @ParameterizedTest
    @CsvSource({"Earth Gravity.markdown, MARKDOWN, Earth Gravity", "Earth Gravity.md, MARKDOWN, Earth Gravity"})
    void of_markdownWithNeitherTitleNorHeading_isTheFileNameWithoutItsSuffix(
            final String fileName, final BookFormat format, final String expected) throws IOException {
        final Path book = write(fileName, LONG_TEXT + "\n\n" + LONG_TEXT + " Again.\n");

        final OriginalTitle title = titleOf(book, format);

        assertThat(title).isEqualTo(new OriginalTitle(expected, TitleOrigin.FILE_NAME, null));
    }

    // IF a blank details title were taken, THEN the card would show an empty title instead of falling through.
    @Test
    void of_epubWithBlankDetailsTitle_fallsThroughToTheFileName() {
        final Path book = TestBooks.epubWithBookTitle(
                dir.resolve("earth-gravity.epub"), List.of(List.of("Prologue", LONG_TEXT)), "   ");

        final OriginalTitle title = titleOf(book, BookFormat.EPUB);

        assertThat(title).isEqualTo(new OriginalTitle("earth-gravity", TitleOrigin.FILE_NAME, null));
    }
}
