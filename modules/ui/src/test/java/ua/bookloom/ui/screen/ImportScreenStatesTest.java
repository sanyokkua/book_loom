package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookCard;
import ua.bookloom.ui.state.ImportState;

/**
 * The import screen's refusing, in-progress and language-mismatch states and its Continue control, read from the real
 * scene; the reporting state and the rest are in {@link ImportScreenTest}. The gestures that start an open are covered
 * there too, in the class comment.
 */
class ImportScreenStatesTest extends ImportScreenTestBase {

    @TempDir
    private Path dir;

    static Stream<Arguments> languagePairs() {
        return Stream.of(
                Arguments.of("en", "English", "uk", "Ukrainian"), Arguments.of("fr", "French", "pl", "Polish"));
    }

    // IF a protected book's refusal lacked its own title, its message or its code, THEN the person would get no reason
    // they could act on; and IF Continue or the card stayed, THEN they could proceed with a book that never opened.
    @Test
    void refusal_protectedBook_showsTitleMessageAndCodeWithNoContinueOrCard() throws TimeoutException {
        final Path source = dir.resolve("secret.epub");
        port.on(
                source,
                Result.err(AppError.of(
                        ErrorCode.validation, "This book is protected", "The book is encrypted and cannot be read.")));
        openImport();

        openBook(source);

        assertThat(isShown("import-refusal")).isTrue();
        assertThat(textOf("import-refusal"))
                .contains("This book is protected")
                .contains("The book is encrypted and cannot be read.")
                .contains("validation");
        assertThat(optional("import-continue")).isNull();
        assertThat(isShown("import-card")).isFalse();
        assertThat(optional("import-choose-another")).isNotNull();
        assertThat(idsStartingWith("import-row-")).isEmpty();
    }

    // IF a file the application cannot read were not refused with its typed code on screen, THEN the person could not
    // tell why it was refused.
    @Test
    void refusal_droppedPdf_reportsValidationAndOffersNoContinue() throws TimeoutException {
        final Path source = dir.resolve("notes.pdf");
        port.on(
                source,
                Result.err(AppError.of(
                        ErrorCode.validation,
                        "This file could not be opened",
                        "The file is damaged or is not a supported book.")));
        openImport();

        openBook(source);

        assertThat(isShown("import-refusal")).isTrue();
        assertThat(textOf("import-refusal")).contains("validation");
        assertThat(optional("import-continue")).isNull();
    }

    // IF the refusal stayed on screen after a good book was chosen, THEN the person could never recover from one bad
    // drop; the screen must follow the view model from refusal to card.
    @Test
    void screen_goodBookAfterARefusal_replacesTheRefusalWithTheCard() throws TimeoutException {
        final Path bad = dir.resolve("secret.epub");
        final Path good = dir.resolve("Frankenstein.epub");
        port.on(bad, Result.err(AppError.of(ErrorCode.validation, "This book is protected", "Encrypted.")));
        port.on(good, Result.ok(BookFixtures.frankenstein()));
        openImport();
        openBook(bad);

        openBook(good);

        assertThat(isShown("import-refusal")).isFalse();
        assertThat(isShown("import-card")).isTrue();
        assertThat(optional("import-continue")).isNotNull();
    }

    // IF the controls stayed live while a book opened, THEN a second choice could be started over the first; and IF the
    // in-progress line were missing, THEN the person could not tell the drop had been accepted.
    @Test
    void screen_bookStillOpening_showsProgressAndDisablesBrowseThenRestoresBoth()
            throws InterruptedException, TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        port.hold();
        openImport();

        onFx(() -> viewModel().open(source));
        port.awaitEntered();
        awaitFx(() -> viewModel().opening().get());

        assertThat(isShown("import-progress")).isTrue();
        assertThat(button("import-browse").isDisable()).isTrue();
        assertThat(isShown("import-card")).isFalse();
        assertThat(optional("import-continue")).isNull();

        port.release();
        awaitFx(() -> !viewModel().opening().get());

        assertThat(isShown("import-progress")).isFalse();
        assertThat(button("import-browse").isDisable()).isFalse();
        assertThat(isShown("import-card")).isTrue();
    }

    // IF the mismatch warning named only one language, or lacked its Continue-anyway control, THEN the person could not
    // judge the disagreement or move on; each language may be shown as its code or its English name.
    @ParameterizedTest(name = "{0} vs {2}")
    @MethodSource("languagePairs")
    void mismatch_stateShownDirectly_namesBothLanguagesAndOffersContinueAnyway(
            final String declared, final String declaredName, final String detected, final String detectedName) {
        openImport();

        onFx(() -> viewModel()
                .showLanguageMismatch(
                        new BookCard(
                                "Frankenstein.epub", BookFormat.EPUB, "Frankenstein", "Mary Shelley", declared, 3, 9),
                        detected));

        assertThat(isShown("import-mismatch")).isTrue();
        assertThat(textOf("import-mismatch"))
                .containsPattern("\\b(" + declared + "|" + declaredName + ")\\b")
                .containsPattern("\\b(" + detected + "|" + detectedName + ")\\b");
        assertThat(optional("import-continue")).isNotNull();
        assertThat(isShown("import-refusal")).isFalse();
    }

    // IF opening a book could reach the warning nothing detects, THEN a person would be warned about a disagreement
    // no detection found.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void mismatch_anyBookOpened_isNeverShown(final String declared) throws TimeoutException {
        final Path source = dir.resolve("any.epub");
        port.on(source, Result.ok(BookFixtures.book("any", BookFormat.EPUB, declared, "T", "A", 1)));
        openImport();

        openBook(source);

        assertThat(isShown("import-mismatch")).isFalse();
        assertThat(state()).isInstanceOf(ImportState.Detected.class);
    }

    // IF Continue were not wired to the workflow, THEN a person with a book open could not reach the brief.
    @Test
    void continueControl_bookOpened_firingItMovesToTheBrief() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        openImport();
        openBook(source);

        onFx(() -> button("import-continue").fire());

        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.BOOK_BRIEF);
    }

    // IF Continue anyway were not wired, THEN a person shown the warning would be stuck on it.
    @Test
    void continueControl_mismatchShown_firingItMovesToTheBrief() {
        openImport();
        onFx(() -> viewModel()
                .showLanguageMismatch(
                        new BookCard("Frankenstein.epub", BookFormat.EPUB, "Frankenstein", "Mary Shelley", "en", 3, 9),
                        "uk"));

        onFx(() -> button("import-continue").fire());

        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.BOOK_BRIEF);
    }
}
