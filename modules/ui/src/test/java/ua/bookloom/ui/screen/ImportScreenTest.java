package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.image.ImageView;
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
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.RecordingReplaceRunPrompt;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

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
                        List.of("file", "format", "chapters", "media", "drm"),
                        List.of("titleAuthor", "declaredLang")),
                Arguments.of(
                        "notes.md",
                        BookFormat.MARKDOWN,
                        null,
                        null,
                        null,
                        List.of("file", "format", "chapters", "media", "drm"),
                        List.of("titleAuthor", "declaredLang")),
                Arguments.of(
                        "titled.md",
                        BookFormat.MARKDOWN,
                        null,
                        "Dune",
                        null,
                        List.of("file", "format", "titleAuthor", "chapters", "media", "drm"),
                        List.of("declaredLang")));
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
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();

        openBook(source);

        assertThat(isShown("import-card")).isTrue();
        assertThat(idsStartingWith("import-row-"))
                .containsExactlyInAnyOrder(
                        "import-row-file",
                        "import-row-format",
                        "import-row-titleAuthor",
                        "import-row-declaredLang",
                        "import-row-chapters",
                        "import-row-media",
                        "import-row-drm");
        assertThat(optional("import-continue")).isNotNull();
        assertThat(button("import-continue").isDisable()).isFalse();
        assertThat(isShown("import-refusal")).isFalse();
        assertThat(isShown("import-mismatch")).isFalse();
        assertThat(isShown("import-progress")).isFalse();
    }

    // IF a row reported the wrong value, THEN the person would see a different book from the one they opened.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "file | Frankenstein.epub",
                "format | EPUB 2.0",
                "titleAuthor | Frankenstein · Mary Shelley",
                "declaredLang | English (en)",
                "chapters | 11 · ~78,000 words",
                "media | 7 · 0",
                "drm | none"
            })
    void card_enBookOpened_rowShowsItsValue(final String row, final String expected) throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();

        openBook(source);

        assertThat(textOf("import-row-" + row)).contains(expected);
    }

    // IF the language row showed the raw declaration or the wrong name, THEN a person would be told a language the
    // application does not use; a tag the JDK names but the catalogue lacks must read like a catalogued one.
    @ParameterizedTest
    @CsvSource({"en-US, en, English (en)", "la, la, Latin (la)", "uk, uk, Ukrainian (uk)"})
    void card_declaredLanguage_isNamedInEnglishWithItsNormalizedTag(
            final String raw, final String declared, final String expected) throws TimeoutException {
        final Path source = dir.resolve("book.epub");
        projects.on(
                source,
                Result.ok(BookFixtures.inspected(
                        "p",
                        BookFormat.EPUB,
                        "3.0",
                        "T",
                        "A",
                        BookFixtures.evidence(raw, declared, declared, LanguageEvidence.Verdict.MATCH),
                        1,
                        10,
                        0,
                        0,
                        null)));
        openImport();

        openBook(source);

        assertThat(textOf("import-row-declaredLang")).contains(expected);
        assertThat(isShown("import-mismatch")).isFalse();
        assertThat(isShown("import-unrecognized")).isFalse();
    }

    // IF a book with a cover drew the placeholder, or one without drew a picture, THEN the card would misreport the
    // book.
    @Test
    void card_bookWithACover_drawsThePictureNotThePlaceholder() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();

        openBook(source);

        assertThat(optional("import-cover")).isInstanceOf(ImageView.class);
        assertThat(optional("import-cover-placeholder")).isNull();
    }

    @Test
    void card_bookWithoutACover_drawsTheNeutralPlaceholder() throws TimeoutException {
        final Path source = dir.resolve("notes.txt");
        projects.on(source, Result.ok(BookFixtures.imported("notes", BookFormat.TXT, null, null, null, 1)));
        openImport();

        openBook(source);

        assertThat(optional("import-cover")).isNull();
        assertThat(required("import-cover-placeholder").getStyleClass()).contains("cover-placeholder");
    }

    // IF a book of 1,499 or 640 words were shown with another figure, THEN the approximate size would mislead.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"640|~640", "999|~999", "1000|~1,000", "1499|~1,000", "1500|~2,000", "78214|~78,000"})
    void card_wordFigure_isExactBelowAThousandAndRoundedToTheNearestThousandFromThere(
            final int words, final String expected) throws TimeoutException {
        final Path source = dir.resolve("book.epub");
        projects.on(
                source,
                Result.ok(BookFixtures.inspected(
                        "p",
                        BookFormat.EPUB,
                        "3.0",
                        null,
                        null,
                        BookFixtures.evidence(null, null, null, LanguageEvidence.Verdict.ABSENT),
                        1,
                        words,
                        0,
                        0,
                        null)));
        openImport();

        openBook(source);

        assertThat(textOf("import-row-chapters")).contains("1 · " + expected + " words");
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
        projects.on(source, Result.ok(BookFixtures.imported(fileName, format, lang, title, author, 4, 2)));
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

    // IF the card kept the mockup's valid badge or a source-language override, THEN it would claim a validation nothing
    // performs and offer a correction the brief owns.
    @Test
    void card_enBookOpened_hasNoValidBadgeAndNoLanguageOverride() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();

        openBook(source);

        assertThat(textOf("import-card")).doesNotContainIgnoringCase("valid").doesNotContainIgnoringCase("override");
        assertThat(required("import-screen").lookupAll(".combo-box, .choice-box, .toggle-button"))
                .isEmpty();
    }

    // IF a drop that carried no file opened something, THEN a stray drag from another application would start an open.
    @Test
    void openDropped_noFiles_opensNothing() {
        openImport();

        onFx(() -> injector.getInstance(ImportController.class).openDropped(List.of()));

        assertThat(projects.imports()).isEmpty();
        assertThat(state()).isEqualTo(new ImportState.Idle());
    }

    // IF a drop of several files opened any but the first, THEN which book became the open one would be arbitrary; and
    // IF none were opened, THEN dropping on the zone would do nothing.
    @Test
    void openDropped_severalFiles_opensOnlyTheFirst() throws TimeoutException {
        final Path first = dir.resolve("first.epub");
        final Path second = dir.resolve("second.epub");
        projects.on(first, Result.ok(BookFixtures.frankensteinImport()));
        openImport();

        onFx(() -> injector.getInstance(ImportController.class).openDropped(List.of(first, second)));
        awaitFx(() -> !viewModel().opening().get());

        assertThat(projects.imports()).containsExactly(first);
        assertThat(state()).isInstanceOf(ImportState.Detected.class);
    }

    // IF a drop on the import screen skipped the guard, THEN a paused translation would be thrown away unasked.
    @Test
    void openDropped_runIsPaused_asksTheQuestionAndImportsNothingYet() throws TimeoutException {
        final Path first = dir.resolve("Frankenstein.epub");
        final Path second = dir.resolve("Dracula.epub");
        projects.on(first, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(first);
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("Frankenstein.epub", null);
        mirror.publishRunState(RunState.PAUSED);
        awaitFx(() -> mirror.runState().get() == RunState.PAUSED);

        onFx(() -> injector.getInstance(ImportController.class).openDropped(List.of(second)));

        assertThat(prompt.asked())
                .containsExactly(
                        new RecordingReplaceRunPrompt.Asked("Frankenstein.epub", "Dracula.epub", RunState.PAUSED));
        assertThat(projects.imports()).containsExactly(first);
    }

    // IF the open book were held by the screen instead of the view model, THEN leaving and returning would forget it
    // while the later screens still read it.
    @Test
    void screen_leftAndReturnedTo_stillReportsTheOpenBook() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
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
        projects.on(first, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(
                second,
                Result.ok(BookFixtures.imported("candide", BookFormat.EPUB, "fr", "Candide", "Voltaire", 1, 1)));
        openImport();
        openBook(first);

        openBook(second);

        assertThat(textOf("import-row-file")).contains("Candide.epub").doesNotContain("Frankenstein.epub");
        assertThat(textOf("import-row-titleAuthor")).contains("Candide");
        assertThat(projects.closedProjects()).hasSize(1);
    }

    // IF a label were still English in Ukrainian, THEN the screen would be half translated.
    @Test
    void screenInUkrainian_dropzoneAndCardLabelsDifferFromEnglish() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
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
