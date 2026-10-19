package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence.Verdict;
import ua.bookloom.ui.BookFixtures;

/** The states a verdict of the inspection publishes, and that none of them raises a toast or a dialog. */
class ImportViewModelVerdictTest extends ImportViewModelTestBase {

    @TempDir
    private Path dir;

    static Stream<Arguments> drmBooks() {
        return Stream.of(
                Arguments.of("Purchased_Novel.epub", "EPUB", "Adobe ADEPT"),
                Arguments.of("Kobzar.fb2.zip", "FB2", "ZIP encryption"),
                Arguments.of("Mystery.epub", "EPUB", null));
    }

    // IF a DRM-protected answer with no project were shown as a card, THEN the person could continue with a book that
    // was never stored; the state must name the scheme and leave no book open, with no toast and no dialog.
    @ParameterizedTest(name = "{0}")
    @MethodSource("drmBooks")
    void open_drmProtectedBook_isDrmBlockedNamingTheSchemeWhenKnown(
            final String fileName, final String type, final @Nullable String scheme) {
        final Path source = dir.resolve(fileName);
        projects.on(source, Result.ok(BookFixtures.drmProtected(type, scheme)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.DrmBlocked(fileName, scheme));
        assertThat(openedBook()).isNull();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
    }

    // IF an unsupported type were reported like a protected book, THEN the person would be told the wrong reason.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"book.pdf, PDF", "report.docx, DOCX", "mystery.bin, Unknown"})
    void open_unsupportedAnswerWithoutAProjectId_isUnsupportedNamingTheDetectedType(
            final String fileName, final String type) {
        final Path source = dir.resolve(fileName);
        projects.on(source, Result.ok(BookFixtures.refused(InspectionVerdict.UNSUPPORTED, type)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Unsupported(fileName, type));
        assertThat(openedBook()).isNull();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
    }

    // IF a language mismatch did not reach the state from the open itself, THEN the warning would be reachable only
    // from a test hook.
    @Test
    void open_bookWhoseTextIsInAnotherLanguage_isDetectedWithTheMismatchAndContinueStaysOpen() {
        final Path source = dir.resolve("Witcher.epub");
        projects.on(
                source,
                Result.ok(BookFixtures.inspected(
                        "w",
                        BookFormat.EPUB,
                        "3.0",
                        "Witcher",
                        "Sapkowski",
                        BookFixtures.evidence("en", "en", "uk", Verdict.MISMATCH),
                        24,
                        100_000,
                        0,
                        0,
                        null)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel))
                .isInstanceOfSatisfying(
                        ImportState.Detected.class,
                        detected -> assertThat(detected.warning()).isEqualTo(new LanguageWarning.Mismatch("en", "uk")));
        assertThat(openedBook()).isNotNull();
    }

    // IF a damaged book of a supported format were shown as DRM or unsupported, THEN the person would be told the
    // wrong reason; it is the Result error, coded validation.
    @Test
    void open_damagedSupportedBook_isRefusedWithTheResultError() {
        final Path source = dir.resolve("broken.epub");
        final AppError damaged = AppError.of(ErrorCode.validation, "Damaged", "The archive is truncated.");
        projects.on(source, Result.err(damaged));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Refused("broken.epub", damaged));
        assertThat(toasts.raised()).isEmpty();
    }
}
