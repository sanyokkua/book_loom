package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The import screen's refusing, blocked, in-progress and language-warning states and its Continue control, read from the real
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

    // IF a refused book's typed reason were lost, THEN the person would get no reason to report; and IF Continue or the
    // fold stayed, THEN they could proceed with a book that never opened. The reason is behind Details.
    @Test
    void refusal_protectedBook_saysTryAgainKeepsTheReasonBehindDetailsWithNoContinueOrFold() throws TimeoutException {
        final Path source = dir.resolve("secret.epub");
        final AppError refusal = AppError.of(
                ErrorCode.validation, "This book is protected", "The book is encrypted and cannot be read.");
        projects.on(source, Result.err(refusal));
        openImport();

        openBook(source);
        onFx(() -> button("import-verdict-details").fire());

        assertThat(textOf("import-verdict-text")).isEqualTo("secret.epub could not be read. Try downloading it again.");
        assertThat(errors.presented()).containsExactly(refusal);
        assertThat(optional("import-continue")).isNull();
        assertThat(optional("import-about")).isNull();
        assertThat(optional("import-verdict-choose")).isNotNull();
        assertThat(idsStartingWith("import-row-")).isEmpty();
    }

    // IF a file the application cannot read were not refused with its typed code on screen, THEN the person could not
    // tell why it was refused.
    @Test
    void refusal_droppedPdf_offersTheValidationDetailsAndNoContinue() throws TimeoutException {
        final Path source = dir.resolve("notes.pdf");
        projects.on(
                source,
                Result.err(AppError.of(
                        ErrorCode.validation,
                        "This file could not be opened",
                        "The file is damaged or is not a supported book.")));
        openImport();

        openBook(source);

        onFx(() -> button("import-verdict-details").fire());

        assertThat(errors.presented()).extracting(AppError::code).containsExactly(ErrorCode.validation);
        assertThat(optional("import-continue")).isNull();
    }

    // IF the refusal stayed on screen after a good book was chosen, THEN the person could never recover from one bad
    // drop; the screen must follow the view model from refusal to card.
    @Test
    void screen_goodBookAfterARefusal_replacesTheRefusalWithTheFold() throws TimeoutException {
        final Path bad = dir.resolve("secret.epub");
        final Path good = dir.resolve("Frankenstein.epub");
        projects.on(bad, Result.err(AppError.of(ErrorCode.validation, "This book is protected", "Encrypted.")));
        projects.on(good, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(bad);

        openBook(good);

        assertThat(optional("import-verdict-choose")).isNull();
        assertThat(isShown("import-about")).isTrue();
        assertThat(optional("import-continue")).isNotNull();
    }

    // IF the controls stayed live while a book opened, THEN a second choice could be started over the first; and IF the
    // in-progress line were missing, THEN the person could not tell the drop had been accepted.
    @Test
    void screen_bookStillOpening_showsProgressAndDisablesBrowseThenRestoresBoth()
            throws InterruptedException, TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        projects.hold();
        openImport();

        onFx(() -> viewModel().open(source));
        projects.awaitEntered();
        awaitFx(() -> viewModel().opening().get());

        assertThat(isShown("import-verdict-bar")).isTrue();
        assertThat(button("import-browse").isDisable()).isTrue();
        assertThat(optional("import-about")).isNull();
        assertThat(optional("import-continue")).isNull();

        projects.release();
        awaitFx(() -> !viewModel().opening().get());

        assertThat(optional("import-verdict-bar")).isNull();
        assertThat(button("import-browse").isDisable()).isFalse();
        assertThat(isShown("import-about")).isTrue();
    }

    // IF the mismatch warning named only one language, or was not reached from an open book, THEN the person could
    // not judge the disagreement; Continue stays available because the source is chosen on the brief.
    @ParameterizedTest(name = "{0} vs {2}")
    @MethodSource("languagePairs")
    void mismatch_bookOpened_namesBothLanguagesAndKeepsContinue(
            final String declared, final String declaredName, final String detected, final String detectedName)
            throws TimeoutException {
        final Path source = dir.resolve("Witcher.epub");
        projects.on(
                source,
                Result.ok(BookFixtures.inspected(
                        "w",
                        BookFormat.EPUB,
                        "3.0",
                        "Witcher",
                        "Sapkowski",
                        BookFixtures.evidence(declared, declared, detected, LanguageEvidence.Verdict.MISMATCH),
                        24,
                        100_000,
                        0,
                        0,
                        null)));
        openImport();

        openBook(source);

        assertThat(textOf("import-verdict-text"))
                .contains("its details say " + declaredName + ", its chapters are marked " + detectedName + ".");
        assertThat(button("import-continue").isDisable()).isFalse();
        assertThat(isShown("import-about")).isTrue();
    }

    // IF an unrecognized declaration were swallowed or shown as a mismatch, THEN the person would not know the source
    // language is left to the model and can be chosen on the brief.
    @Test
    void unrecognized_bookOpened_quotesTheRawCodeAndSendsThePersonToTheBrief() throws TimeoutException {
        final Path source = dir.resolve("odd.epub");
        projects.on(
                source,
                Result.ok(BookFixtures.inspected(
                        "o",
                        BookFormat.EPUB,
                        "3.0",
                        "T",
                        "A",
                        BookFixtures.evidence("xx-yy", null, "en", LanguageEvidence.Verdict.UNRECOGNIZED),
                        2,
                        50,
                        0,
                        0,
                        null)));
        openImport();

        openBook(source);

        assertThat(textOf("import-verdict-text")).contains("xx-yy").contains("Book Brief");
        assertThat(textOf("import-verdict-text")).doesNotContain("two languages");
        assertThat(optional("import-row-declaredLang")).isNull();
        assertThat(button("import-continue").isDisable()).isFalse();
    }

    // IF a DRM-protected book rendered like any refusal, THEN the person would not learn it is encrypted or by what.
    @Test
    void drmBlocked_adobeAdeptBook_showsTheLockedLineWithItsSchemeAndOnlyChooseAnother() throws TimeoutException {
        final Path source = dir.resolve("Purchased_Novel.epub");
        projects.on(source, Result.ok(BookFixtures.drmProtected("EPUB", "Adobe ADEPT")));
        openImport();

        openBook(source);

        assertThat(textOf("import-verdict-text"))
                .isEqualTo("Purchased_Novel.epub is locked by the shop it came from. Encryption: Adobe ADEPT.");
        assertOnlyChooseAnotherFooter();
    }

    @Test
    void drmBlocked_schemeNotIdentified_omitsTheEncryptionSentence() throws TimeoutException {
        final Path source = dir.resolve("Mystery.epub");
        projects.on(source, Result.ok(BookFixtures.drmProtected("EPUB", null)));
        openImport();

        openBook(source);

        assertThat(textOf("import-verdict-text")).isEqualTo("Mystery.epub is locked by the shop it came from.");
    }

    // IF a PDF rendered like a damaged book, THEN the person would not learn the file is simply not a supported type.
    @Test
    void unsupported_pdf_namesTheTypeAndTheSupportedFormatsAndOnlyChooseAnother() throws TimeoutException {
        final Path source = dir.resolve("book.pdf");
        projects.on(source, Result.ok(BookFixtures.refused(InspectionVerdict.UNSUPPORTED, "PDF")));
        openImport();

        openBook(source);

        assertThat(textOf("import-verdict-text"))
                .startsWith("book.pdf is PDF, which is not supported.")
                .endsWith("It reads EPUB, FB2, Markdown and plain text.");
        assertOnlyChooseAnotherFooter();
    }

    // IF a refused book's footer offered Continue or Cancel, THEN a person could move on with no book.
    @Test
    void refusal_damagedBook_footerIsOnlyChooseAnother() throws TimeoutException {
        final Path source = dir.resolve("broken.epub");
        projects.on(source, Result.err(AppError.of(ErrorCode.validation, "Damaged", "Truncated.")));
        openImport();

        openBook(source);

        assertOnlyChooseAnotherFooter();
    }

    // IF Cancel did not release the book, THEN an abandoned import would stay stored and open.
    @Test
    void cancel_bookOpened_releasesTheProjectAndReturnsToTheDropZone() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();
        openBook(source);

        onFx(() -> button("import-cancel").fire());
        awaitFx(() -> projects.closedProjects().size() == 1);

        assertThat(projects.closedProjects()).containsExactly("p1");
        assertThat(state()).isEqualTo(new ImportState.Idle());
        assertThat(optional("import-about")).isNull();
        assertThat(isShown("import-dropzone")).isTrue();
    }

    // IF Cancel dropped a project that holds work without asking, THEN one click would lose the brief, the names and
    // the translated segments; the project is closed only after the confirming button.
    @Test
    void cancel_projectWithProgress_asksFirstAndReleasesOnlyWhenConfirmed() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();
        openBook(source);
        onFx(() -> injector.getInstance(WorkflowProgress.class).markDone(ViewNames.EXPORT));

        onFx(() -> button("import-cancel").fire());
        final boolean asked = isShown("confirm-card");
        final List<String> closedWhileAsking = List.copyOf(projects.closedProjects());
        onFx(() -> button("confirm-yes").fire());
        awaitFx(() -> projects.closedProjects().size() == 1);

        assertThat(asked).isTrue();
        assertThat(closedWhileAsking).isEmpty();
        assertThat(state()).isEqualTo(new ImportState.Idle());
    }

    // IF "No" still released the book, THEN the question would not protect anything.
    @Test
    void cancel_projectWithProgressAndAnsweredNo_keepsTheBookOpen() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();
        openBook(source);
        onFx(() -> injector.getInstance(WorkflowProgress.class).markDone(ViewNames.EXPORT));

        onFx(() -> button("import-cancel").fire());
        onFx(() -> button("confirm-cancel").fire());

        assertThat(projects.closedProjects()).isEmpty();
        assertThat(isShown("import-about")).isTrue();
    }

    private void assertOnlyChooseAnotherFooter() {
        assertThat(optional("import-continue")).isNull();
        assertThat(optional("import-cancel")).isNull();
        assertThat(optional("import-about")).isNull();
        assertThat(button("import-verdict-choose").getText()).isEqualTo("Choose another file…");
        assertThat(idsStartingWith("import-row-")).isEmpty();
    }

    // IF Continue were not wired to the workflow, THEN a person with a book open could not reach the brief.
    @Test
    void continueControl_bookOpened_firingItMovesToTheBrief() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(source);

        onFx(() -> button("import-continue").fire());

        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.BOOK_BRIEF);
    }
}
