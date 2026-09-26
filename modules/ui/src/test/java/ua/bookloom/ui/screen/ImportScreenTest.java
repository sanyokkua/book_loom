package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ImportState;

/**
 * The import screen the application builds, read from the real scene: what each of its four states shows, what is
 * disabled while a book opens, what Continue does, and how it reads in Ukrainian. "Wired" is asserted behaviourally,
 * by driving the view model and reading the node, and by firing the node and reading where the application went.
 *
 * <p>Two things are deliberately not driven through the real gesture. The JavaFX headless glass platform throws
 * "Not supported yet" for any drag-and-drop clipboard read, so a {@code DragEvent} can be built but its files cannot
 * be read, and the file chooser is a native dialog with no headless implementation; both funnel into the same
 * {@code ImportViewModel.open}, which is driven here and in {@code ImportViewModelTest}. The running-app check in the
 * Definition of Done covers the two gestures themselves.
 */
class ImportScreenTest extends ImportScreenTestBase {

    @TempDir
    private Path dir;

    static Stream<Arguments> bookWithFewerFields() {
        return Stream.of(
                Arguments.of(
                        "notes.txt",
                        BookFormat.TXT,
                        null,
                        null,
                        null,
                        List.of("file", "format", "units", "segments"),
                        List.of("title", "author", "declaredLang")),
                Arguments.of(
                        "notes.md",
                        BookFormat.MARKDOWN,
                        null,
                        null,
                        null,
                        List.of("file", "format", "units", "segments"),
                        List.of("title", "author", "declaredLang")),
                Arguments.of(
                        "titled.md",
                        BookFormat.MARKDOWN,
                        null,
                        "Dune",
                        null,
                        List.of("file", "format", "title", "units", "segments"),
                        List.of("author", "declaredLang")));
    }

    // IF a state's parts were shown before anything was chosen, THEN the person would see a card, a progress bar, a
    // refusal or a warning about nothing.
    @ParameterizedTest
    @ValueSource(strings = {"import-card", "import-refusal", "import-mismatch", "import-progress"})
    void screen_nothingChosenYet_doesNotShowTheOtherStatesParts(final String id) {
        openImport();

        assertThat(isShown(id)).isFalse();
    }

    // IF the drop zone or the browse control were missing or disabled at rest, THEN a person could not choose a book at
    // all; and IF the drop zone did not accept a drag, THEN dropping a file would do nothing.
    @Test
    void screen_nothingChosenYet_showsDropzoneAndEnabledBrowseWithDragHandlers() {
        openImport();

        assertThat(isShown("import-dropzone")).isTrue();
        assertThat(required("import-dropzone").getOnDragOver()).isNotNull();
        assertThat(required("import-dropzone").getOnDragDropped()).isNotNull();
        assertThat(button("import-browse").getText()).isEqualTo("Browse files…");
        assertThat(button("import-browse").isDisable()).isFalse();
    }

    // IF Continue or any card row existed before a book was opened, THEN a person could proceed with no book.
    @Test
    void screen_nothingChosenYet_hasNoContinueAndNoCardRows() {
        openImport();

        assertThat(optional("import-continue")).isNull();
        assertThat(optional("import-choose-another")).isNull();
        assertThat(idsStartingWith("import-row-")).isEmpty();
    }

    // IF the card omitted a row the parse carries, showed one it does not, or offered no way on, THEN the screen would
    // misreport the book.
    @Test
    void card_enBookOpened_showsEverySevenRowsAndContinueAndNoRefusal() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();

        openBook(source);

        assertThat(isShown("import-card")).isTrue();
        assertThat(idsStartingWith("import-row-"))
                .containsExactlyInAnyOrder(
                        "import-row-file",
                        "import-row-format",
                        "import-row-title",
                        "import-row-author",
                        "import-row-declaredLang",
                        "import-row-units",
                        "import-row-segments");
        assertThat(optional("import-continue")).isNotNull();
        assertThat(button("import-continue").isDisable()).isFalse();
        assertThat(isShown("import-refusal")).isFalse();
        assertThat(isShown("import-mismatch")).isFalse();
        assertThat(isShown("import-progress")).isFalse();
    }

    // IF a row reported the wrong value, THEN the person would see a different book from the one they opened.
    @ParameterizedTest
    @CsvSource({
        "file, Frankenstein.epub",
        "format, EPUB",
        "title, Frankenstein",
        "author, Mary Shelley",
        "units, 3",
        "segments, 9"
    })
    void card_enBookOpened_rowShowsItsValue(final String row, final String expected) throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();

        openBook(source);

        assertThat(textOf("import-row-" + row)).containsIgnoringCase(expected);
    }

    // IF the declared language row showed anything other than the declared code, THEN a person would be told a language
    // the book does not declare.
    @Test
    void card_enBookOpened_declaredLanguageRowShowsTheDeclaredCode() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();

        openBook(source);

        assertThat(textOf("import-row-declaredLang")).containsPattern("\\ben\\b");
    }

    // IF a row for an undeclared field were shown as a placeholder, THEN the card would state something the file never
    // said; the row must be absent and nothing must be reported as an error.
    @ParameterizedTest(name = "{0}")
    @MethodSource("bookWithFewerFields")
    void card_bookDeclaringLittle_omitsTheUndeclaredRowsAndShowsNoError(
            final String fileName,
            final BookFormat format,
            final @Nullable String lang,
            final @Nullable String title,
            final @Nullable String author,
            final List<String> presentRows,
            final List<String> absentRows)
            throws TimeoutException {
        final Path source = dir.resolve(fileName);
        port.on(source, Result.ok(BookFixtures.book(fileName, format, lang, title, author, 4, 2)));
        openImport();

        openBook(source);

        assertThat(idsStartingWith("import-row-"))
                .containsExactlyInAnyOrderElementsOf(
                        presentRows.stream().map(row -> "import-row-" + row).toList())
                .doesNotContainAnyElementsOf(
                        absentRows.stream().map(row -> "import-row-" + row).toList());
        assertThat(isShown("import-refusal")).isFalse();
        assertThat(optional("import-continue")).isNotNull();
    }

    // IF the card mentioned a figure the parsed book does not carry, THEN it would be inventing it.
    @ParameterizedTest
    @ValueSource(strings = {"chapter", "word", "image", "font", "cover", "detected"})
    void card_enBookOpened_neverMentionsWhatTheParseDoesNotCarry(final String invented) throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();

        openBook(source);

        assertThat(textOf("import-card")).doesNotContainIgnoringCase(invented);
        assertThat(idsStartingWith("import-"))
                .noneMatch(id -> id.toLowerCase(Locale.ROOT).contains(invented));
    }

    // IF a drop that carried no file opened something, THEN a stray drag from another application would start an open.
    @Test
    void openDropped_noFiles_opensNothing() {
        openImport();

        onFx(() -> injector.getInstance(ImportController.class).openDropped(List.of()));

        assertThat(port.openedPaths()).isEmpty();
        assertThat(state()).isEqualTo(new ImportState.Idle());
    }

    // IF a drop of several files opened any but the first, THEN which book became the open one would be arbitrary; and
    // IF none were opened, THEN dropping on the zone would do nothing.
    @Test
    void openDropped_severalFiles_opensOnlyTheFirst() throws TimeoutException {
        final Path first = dir.resolve("first.epub");
        final Path second = dir.resolve("second.epub");
        port.on(first, Result.ok(BookFixtures.frankenstein()));
        openImport();

        onFx(() -> injector.getInstance(ImportController.class).openDropped(List.of(first, second)));
        awaitFx(() -> !viewModel().opening().get());

        assertThat(port.openedPaths()).containsExactly(first);
        assertThat(state()).isInstanceOf(ImportState.Detected.class);
    }

    // IF the open book were held by the screen instead of the view model, THEN leaving and returning would forget it
    // while the later screens still read it.
    @Test
    void screen_leftAndReturnedTo_stillReportsTheOpenBook() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();
        openBook(source);
        onFx(() -> shell.activate(ViewNames.SETTINGS));

        openImport();

        assertThat(isShown("import-card")).isTrue();
        assertThat(textOf("import-row-file")).contains("Frankenstein.epub");
    }

    // IF the card still showed the first book after a second was opened, THEN the person would translate a different
    // book from the one on screen.
    @Test
    void card_secondBookOpened_reportsTheSecondAndTheFirstIsReleased() throws TimeoutException {
        final Path first = dir.resolve("Frankenstein.epub");
        final Path second = dir.resolve("Candide.epub");
        port.on(first, Result.ok(BookFixtures.frankenstein()));
        port.on(second, Result.ok(BookFixtures.book("candide", BookFormat.EPUB, "fr", "Candide", "Voltaire", 1, 1)));
        openImport();
        openBook(first);

        openBook(second);

        assertThat(textOf("import-row-file")).contains("Candide.epub").doesNotContain("Frankenstein.epub");
        assertThat(textOf("import-row-title")).contains("Candide");
        assertThat(port.closedDocuments()).hasSize(1);
    }

    // IF a label were still English in Ukrainian, THEN the screen would be half translated.
    @Test
    void screenInUkrainian_dropzoneAndCardLabelsDifferFromEnglish() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();
        final String englishBrowse = button("import-browse").getText();
        openBook(source);
        final List<String> englishRow = textsUnder(required("import-row-file"));

        useLocale(Locale.forLanguageTag("uk"));
        openImport();
        final String ukrainianBrowse = button("import-browse").getText();
        openBook(source);
        final List<String> ukrainianRow = textsUnder(required("import-row-file"));

        assertThat(ukrainianBrowse).isNotBlank().isNotEqualTo(englishBrowse);
        assertThat(ukrainianRow).isNotEqualTo(englishRow);
        assertThat(String.join(" ", ukrainianRow)).contains("Frankenstein.epub");
    }
}
