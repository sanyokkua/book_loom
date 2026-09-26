package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingToasts.Raised;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * What the import view model makes of the port's answer: the card a parsed book becomes, the refusing state a typed
 * error becomes, and where each kind of failure is routed. Threading and the release of a replaced book are in
 * {@link ImportViewModelLifecycleTest}.
 */
class ImportViewModelTest extends ImportViewModelTestBase {

    @TempDir
    private Path dir;

    static Stream<Arguments> partialMetadata() {
        return Stream.of(
                Arguments.of("title only", "Dune", null, "Dune", null),
                Arguments.of("author only", null, "Frank Herbert", null, "Frank Herbert"),
                Arguments.of("neither", null, null, null, null));
    }

    // IF a fresh view model already claimed a book, an opening or a refusal, THEN the screen would open on a state
    // nobody caused.
    @Test
    void state_freshViewModel_isIdleWithNoBookAndNotOpening() {
        final ImportViewModel viewModel = viewModel();

        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
        assertThat(openedBookOf(viewModel)).isNull();
        assertThat(isOpening(viewModel)).isFalse();
    }

    // IF the card carried anything other than what the parse found, or the counts were not the parse's, THEN the
    // screen would report a book the person did not open; and IF the book were not kept, THEN the brief and the
    // structure screens would have nothing to read.
    @Test
    void open_enBook_detectedCardCarriesEveryFieldAndCounts() {
        final Path source = dir.resolve("Frankenstein.epub");
        final Document book = BookFixtures.frankenstein();
        port.on(source, Result.ok(book));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel))
                .isEqualTo(new ImportState.Detected(new BookCard(
                        "Frankenstein.epub", BookFormat.EPUB, "Frankenstein", "Mary Shelley", "en", 3, 9)));
        assertThat(openedBookOf(viewModel)).isEqualTo(new OpenedBook(source, book));
        assertThat(isOpening(viewModel)).isFalse();
    }

    // IF a plain-text book with no title, author or language got placeholder values, THEN the card would state things
    // the file never said; the three absent fields must be null so the view can omit their rows.
    @Test
    void open_txtBook_reportsNameFormatAndCountsWithThreeFieldsAbsentAndNoError() {
        final Path source = dir.resolve("notes.txt");
        port.on(source, Result.ok(BookFixtures.book("notes", BookFormat.TXT, null, null, null, 5, 1)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel))
                .isEqualTo(new ImportState.Detected(new BookCard("notes.txt", BookFormat.TXT, null, null, null, 2, 6)));
        assertThat(errors.presented()).isEmpty();
    }

    // IF a Markdown book whose frontmatter states nothing were given a title, THEN the card would invent one.
    @Test
    void open_markdownWithoutFrontmatter_titleAndAuthorAreAbsentCountsReported() {
        final Path source = dir.resolve("notes.md");
        port.on(source, Result.ok(BookFixtures.book("notes-md", BookFormat.MARKDOWN, null, null, null, 4)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel))
                .isEqualTo(new ImportState.Detected(
                        new BookCard("notes.md", BookFormat.MARKDOWN, null, null, null, 1, 4)));
    }

    // IF one missing metadata key hid or blanked the other, THEN a book declaring only its title would lose it.
    @ParameterizedTest(name = "{0}")
    @MethodSource("partialMetadata")
    void open_partialMetadata_eachDeclaredFieldSurvivesTheOtherBeingAbsent(
            final String label,
            final @Nullable String title,
            final @Nullable String author,
            final @Nullable String expectedTitle,
            final @Nullable String expectedAuthor) {
        final Path source = dir.resolve("book.epub");
        port.on(source, Result.ok(BookFixtures.book("partial", BookFormat.EPUB, null, title, author, 1)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel))
                .isEqualTo(new ImportState.Detected(
                        new BookCard("book.epub", BookFormat.EPUB, expectedTitle, expectedAuthor, null, 1, 1)));
    }

    // IF a book with a declared language of any value ended in the mismatch state, THEN a warning would appear that no
    // detection produced; every successful open must be the reporting state.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk", "fr"})
    void open_anyDeclaredLanguage_isDetectedNeverLanguageMismatch(final String declared) {
        final Path source = dir.resolve("any.epub");
        port.on(source, Result.ok(BookFixtures.book("any", BookFormat.EPUB, declared, "T", "A", 1)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Detected.class);
    }

    // IF a refusal ever became the mismatch state, THEN a protected book would be reported as a language problem.
    @Test
    void open_refusedBook_isRefusedNeverLanguageMismatch() {
        final Path source = dir.resolve("secret.epub");
        port.on(source, Result.err(protectedError()));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Refused.class);
    }

    // IF a protected book were shown as an ordinary failure, or left a book open, THEN the person would get no reason
    // and the next screens would read a book that was never opened; and a refusal is in place, never a dialog or toast.
    @Test
    void open_protectedBook_refusedInPlaceWithItsErrorAndNoBookOrToastOrDialog() {
        final Path source = dir.resolve("secret.epub");
        final AppError refusal = protectedError();
        port.on(source, Result.err(refusal));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Refused("secret.epub", refusal));
        assertThat(openedBookOf(viewModel)).isNull();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(isOpening(viewModel)).isFalse();
    }

    // IF a file the application cannot read were not refused with its typed validation code, THEN the screen could not
    // tell a person what kind of refusal it is.
    @Test
    void open_pdfFile_refusedCarryingValidation() {
        final Path source = dir.resolve("notes.pdf");
        final AppError refusal = AppError.of(
                ErrorCode.validation, "This file could not be opened", "The file is damaged or not a supported book.");
        port.on(source, Result.err(refusal));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel)).isInstanceOfSatisfying(ImportState.Refused.class, refused -> {
            assertThat(refused.fileName()).isEqualTo("notes.pdf");
            assertThat(refused.error().code()).isEqualTo(ErrorCode.validation);
        });
        assertThat(openedBookOf(viewModel)).isNull();
    }

    // IF a success raised no toast, or more than one, or named another file, THEN the confirmation would be missing or
    // wrong; and IF a refusal raised one, THEN a person would be told a book opened that did not.
    @Test
    void open_success_raisesExactlyOneSuccessToastNamingTheFile() {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(toasts.raised())
                .containsExactly(new Raised("success", MessageKey.TOAST_BOOK_OPENED, List.of("Frankenstein.epub")));
    }

    // IF a refusal raised a toast, THEN a person would be told a book opened that did not.
    @Test
    void open_refusal_raisesNoToast() {
        final Path source = dir.resolve("secret.epub");
        port.on(source, Result.err(protectedError()));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(toasts.raised()).isEmpty();
    }

    // IF an unexpected or busy failure were only written in place, THEN it would lack the expandable-details dialog the
    // notification spec gives it; and IF it became a refusal, THEN a fault would read as a bad file.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"internal", "busy"})
    void open_internalOrBusyWithNoPreviousBook_goesToThePresenterAndStaysIdleWithNoRefusal(final ErrorCode code) {
        final Path source = dir.resolve("any.epub");
        final AppError fault = AppError.of(code, "Could not open", "The book could not be opened.");
        port.on(source, Result.err(fault));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(errors.presented()).containsExactly(fault);
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
        assertThat(openedBookOf(viewModel)).isNull();
        assertThat(toasts.raised()).isEmpty();
        assertThat(isOpening(viewModel)).isFalse();
    }

    // IF a refusal a person can fix went to the blocking dialog, THEN the specified in-place treatment would be
    // missing.
    @Test
    void open_validationRefusal_neverReachesThePresenter() {
        final Path source = dir.resolve("notes.pdf");
        port.on(source, Result.err(AppError.of(ErrorCode.validation, "This file could not be opened", "Damaged.")));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(errors.presented()).isEmpty();
    }

    // IF the refusing state stuck after a good file was chosen, THEN a person could never recover from one bad drop.
    @Test
    void open_goodBookAfterARefusal_replacesTheRefusalWithTheCard() {
        final Path bad = dir.resolve("secret.epub");
        final Path good = dir.resolve("Frankenstein.epub");
        port.on(bad, Result.err(protectedError()));
        port.on(good, Result.ok(BookFixtures.frankenstein()));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, bad);

        open(viewModel, good);

        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Detected.class);
        assertThat(openedBookOf(viewModel)).isNotNull();
    }

    // IF the mismatch state could not be put on the screen directly, THEN its rendering would be covered by nothing;
    // the view model must publish exactly the card and detected language it was given.
    @Test
    void showLanguageMismatch_cardAndDetectedLanguage_publishesThatState() {
        final BookCard card =
                new BookCard("Frankenstein.epub", BookFormat.EPUB, "Frankenstein", "Mary Shelley", "en", 3, 9);
        final ImportViewModel viewModel = viewModel();

        onFx(() -> {
            viewModel.showLanguageMismatch(card, "uk");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.LanguageMismatch(card, "uk"));
    }

    private static AppError protectedError() {
        return AppError.of(
                ErrorCode.validation, "This book is protected", "The book is encrypted and cannot be translated.");
    }
}
