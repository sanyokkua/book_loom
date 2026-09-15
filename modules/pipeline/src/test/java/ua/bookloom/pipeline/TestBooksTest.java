package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.document.DocumentModule;

/** Proves every pipeline book builder produces a real document the document module can open. */
class TestBooksTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    // Every generated format carries its configured content and language through the real parser.
    @ParameterizedTest(name = "{0}")
    @MethodSource("bookCases")
    void generatedBook_supportedFormat_opensWithExpectedShape(
            final String name,
            final Function<Path, Path> writer,
            final BookFormat format,
            final String language,
            final int units,
            final int segments) {
        final Document document =
                Objects.requireNonNull(documents.open(writer.apply(tempDir)).data(), "document");

        assertThat(document.format()).isEqualTo(format);
        assertThat(document.declaredLang()).isEqualTo(language);
        assertThat(document.units()).hasSize(units);
        assertThat(document.units()).flatExtracting(unit -> unit.segments()).hasSize(segments);
        assertThat(documents.close(document).data()).isTrue();
    }

    private static Stream<Arguments> bookCases() {
        return Stream.concat(containerBookCases(), textBookCases());
    }

    private static Stream<Arguments> containerBookCases() {
        return Stream.of(
                Arguments.of(
                        "epub",
                        (Function<Path, Path>) dir -> TestBooks.epub(
                                dir.resolve("Book.epub"), List.of(List.of("One."), List.of("Two.", "Three.")), "en"),
                        BookFormat.EPUB,
                        "en",
                        2,
                        3),
                Arguments.of(
                        "fb2",
                        (Function<Path, Path>)
                                dir -> TestBooks.fb2(dir.resolve("Book.fb2"), List.of("One.", "Two."), "uk"),
                        BookFormat.FB2,
                        "uk",
                        1,
                        2),
                Arguments.of(
                        "zipped fb2",
                        (Function<Path, Path>)
                                dir -> TestBooks.zippedFb2(dir.resolve("Book.fb2.zip"), List.of("One."), "de"),
                        BookFormat.FB2,
                        "de",
                        1,
                        1));
    }

    private static Stream<Arguments> textBookCases() {
        return Stream.of(
                Arguments.of(
                        "markdown",
                        (Function<Path, Path>) dir -> TestBooks.markdown(dir.resolve("Book.md"), "One.", "en"),
                        BookFormat.MARKDOWN,
                        "en",
                        1,
                        1),
                Arguments.of(
                        "txt",
                        (Function<Path, Path>) dir -> TestBooks.txt(dir.resolve("Book.txt"), "One."),
                        BookFormat.TXT,
                        null,
                        1,
                        1));
    }
}
