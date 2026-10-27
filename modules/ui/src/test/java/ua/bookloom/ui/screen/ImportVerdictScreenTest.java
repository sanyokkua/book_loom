package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ProgressBar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ProjectFilter;
import ua.bookloom.ui.state.ProjectsViewModel;

/** The one verdict line of the import page per state, read from the real scene: its words, its controls, its absences. */
class ImportVerdictScreenTest extends ImportScreenTestBase {

    private static final String ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path dir;

    private void open(final String fileName, final Result<ImportedBook> answer) throws TimeoutException {
        final Path source = dir.resolve(fileName);
        projects.on(source, answer);
        openImport();
        openBook(source);
    }

    // IF a ready book had a Choose another file button, THEN the line would offer an escape from a good result.
    @Test
    void verdict_readyBook_saysItCanTranslateAndOffersNoRefusalControls() throws TimeoutException {
        open("book.epub", Result.ok(BookFixtures.frankensteinImport()));

        assertThat(textOf("import-verdict-text")).startsWith("BookLoom can translate this book: 3 chapters");
        assertThat(textOf("import-verdict-text")).endsWith("Your file is never changed.");
        assertThat(optional("import-verdict-choose")).isNull();
        assertThat(optional("import-verdict-show")).isNull();
        assertThat(optional("import-continue")).isNotNull();
    }

    // IF the bar were determinate, THEN it would claim a chapter count the parsers never report.
    @Test
    void verdict_bookStillReading_showsAnIndeterminateBarReadingAndCancel()
            throws InterruptedException, TimeoutException {
        final Path source = dir.resolve("book.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        projects.hold();
        openImport();

        onFx(() -> viewModel().open(source));
        projects.awaitEntered();
        awaitFx(() -> viewModel().opening().get());

        assertThat(textOf("import-verdict-text")).isEqualTo("Reading...");
        assertThat(ThemeTestSupport.onFx(() -> ((ProgressBar) required("import-verdict-bar")).isIndeterminate()))
                .isTrue();
        assertThat(optional("import-verdict-cancel")).isNotNull();
        projects.release();
    }

    @Test
    void verdict_twoLanguageMarks_namesBothAndStillAllowsContinue() throws TimeoutException {
        open(
                "book.epub",
                Result.ok(BookFixtures.inspected(
                        "p",
                        BookFormat.EPUB,
                        "3.0",
                        "T",
                        "A",
                        BookFixtures.evidence("en", "en", "uk", LanguageEvidence.Verdict.MISMATCH),
                        3,
                        900,
                        0,
                        0,
                        null)));

        assertThat(textOf("import-verdict-text"))
                .startsWith(
                        "The file names two languages: its details say English, its chapters are marked Ukrainian.");
        assertThat(optional("import-continue")).isNotNull();
    }

    @Test
    void verdict_drmBook_nameTheSchemeOffersChooseAnotherAndNoContinue() throws TimeoutException {
        open("Purchased_Novel.epub", Result.ok(BookFixtures.drmProtected("EPUB", "Adobe ADEPT")));

        assertThat(textOf("import-verdict-text"))
                .isEqualTo("Purchased_Novel.epub is locked by the shop it came from. Encryption: Adobe ADEPT.");
        assertThat(button("import-verdict-choose").getText()).isEqualTo("Choose another file...");
        assertThat(optional("import-continue")).isNull();
    }

    @Test
    void verdict_unsupportedFile_namesItsTypeAndOffersChooseAnother() throws TimeoutException {
        open("book.pdf", Result.ok(BookFixtures.refused(InspectionVerdict.UNSUPPORTED, "PDF")));

        assertThat(textOf("import-verdict-text"))
                .isEqualTo("book.pdf is PDF, which is not supported. It reads EPUB, FB2, Markdown and plain text.");
        assertThat(optional("import-verdict-choose")).isNotNull();
    }

    // IF an empty book offered Continue, THEN a project of nothing would be set up and translated.
    @Test
    void verdict_bookWithNoText_saysSoOffersChooseAnotherAndNoContinue() throws TimeoutException {
        open("empty.txt", Result.ok(BookFixtures.refused(InspectionVerdict.NO_TEXT, "TXT")));

        assertThat(textOf("import-verdict-text")).isEqualTo("This book has no text to translate.");
        assertThat(optional("import-verdict-choose")).isNotNull();
        assertThat(optional("import-continue")).isNull();
        assertThat(viewModel().state().get()).isEqualTo(new ImportState.NoText("empty.txt"));
    }

    // IF a file the inspection found damaged were worded as an unknown type, THEN "Unknown" would read as a format.
    @Test
    void verdict_damagedByInspection_namesTheFormatItCouldNotBeReadAs() throws TimeoutException {
        open("corrupt.epub", Result.ok(BookFixtures.refused(InspectionVerdict.DAMAGED, "EPUB")));

        assertThat(textOf("import-verdict-text"))
                .isEqualTo("corrupt.epub could not be read as an EPUB. The file may be damaged.")
                .doesNotContain("Unknown");
        assertThat(optional("import-continue")).isNull();
    }

    // IF the damaged verdict hid the typed reason, THEN a person could not report what went wrong.
    @Test
    void verdict_damagedBook_saysTryAgainAndKeepsTheReasonBehindDetails() throws TimeoutException {
        open("broken.epub", Result.err(AppError.of(ErrorCode.validation, "This file could not be opened", "Damaged.")));

        assertThat(textOf("import-verdict-text"))
                .isEqualTo("broken.epub could not be read as an EPUB. The file may be damaged.");
        assertThat(button("import-verdict-details").getText()).isEqualTo("Details");
        assertThat(optional("import-verdict-choose")).isNotNull();
        assertThat(optional("import-continue")).isNull();
    }

    private Path dropKnownBookOfTwoProjects() throws IOException, TimeoutException {
        final Path file = Files.write(dir.resolve("earth.epub"), new byte[] {'a', 'b', 'c'});
        projects.willList(
                List.of(summaryOf("p1", "Earth Gravity · EN → UK"), summaryOf("p2", "Earth Gravity · EN → PL")));
        openImport();
        onFx(() -> injector.getInstance(ImportController.class).openDropped(List.of(file)));
        awaitFx(() -> viewModel().state().get() instanceof ImportState.AlreadyInProjects);
        return file;
    }

    private static ProjectSummary summaryOf(final String id, final String name) {
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        id,
                        "earth.epub",
                        ABC_HASH,
                        "en",
                        "uk",
                        NameOrigin.AUTO,
                        name,
                        Instant.parse("2026-10-02T10:00:00Z")),
                3,
                null,
                null);
    }

    // IF a known book opened a dialog or created a project, THEN the person would be asked about nothing.
    @Test
    void verdict_bookAlreadyInProjects_namesBothOffersShowThemAndCreatesNothing() throws IOException, TimeoutException {
        dropKnownBookOfTwoProjects();

        assertThat(textOf("import-verdict-text"))
                .isEqualTo("Already in projects: Earth Gravity · EN → UK, Earth Gravity · EN → PL.");
        assertThat(button("import-verdict-show").getText()).isEqualTo("Show them");
        assertThat(optional("import-continue")).isNull();
        assertThat(optional("open-another-card")).isNull();
        assertThat(projects.imports()).isEmpty();
        assertThat(projects.removedProjects()).isEmpty();
    }

    @Test
    void showThem_pressed_opensProjectsOnThatBook() throws IOException, TimeoutException {
        dropKnownBookOfTwoProjects();

        onFx(() -> button("import-verdict-show").fire());

        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(Navigator.class).currentView().get()))
                .isEqualTo(ViewNames.PROJECTS);
        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(ProjectsViewModel.class).search().get()))
                .isEqualTo("Earth Gravity");
        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(ProjectsViewModel.class).filter().get()))
                .isEqualTo(ProjectFilter.ALL);
    }

    @ParameterizedTest
    @CsvSource({"en, Choose another file...", "uk, Обрати інший файл..."})
    void verdict_refusal_labelsTheChooseButtonInTheInterfaceLanguage(final String language, final String label)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));

        open("Purchased_Novel.epub", Result.ok(BookFixtures.drmProtected("EPUB", null)));

        assertThat(button("import-verdict-choose").getText()).isEqualTo(label);
    }
}
