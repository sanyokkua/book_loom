package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.ui.BookFixtures;

/** Opening a book is only ever a create: no stored project is removed unless the person chose to replace one. */
class ImportViewModelNeverRemovesTest extends ImportViewModelTestBase {

    // IF open() removed the stored project of the same file, THEN importing again would silently delete a translation.
    @Test
    void open_projectOfTheSameFileIsStored_createsAndRemovesNothing() {
        final Path source = Path.of("/books/Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        projects.onDecided(source, 5);
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(projects.removedProjects()).isEmpty();
        assertThat(projects.imports()).containsExactly(source);
        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Detected.class);
    }

    // IF the old project went first, THEN a book that cannot be opened would leave the person with no project at all.
    @Test
    void openReplacing_storedProject_createsThenRemovesIt() {
        final Path source = Path.of("/books/Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = viewModel();

        replacing(viewModel, source, "p9");

        assertThat(projects.events()).containsSubsequence("import /books/Frankenstein.epub", "remove p9");
    }

    @Test
    void openReplacing_newBookFailsToOpen_keepsTheOldProject() {
        final Path source = Path.of("/books/broken.epub");
        projects.on(source, Result.err(AppError.of(ErrorCode.validation, "Unreadable", "The file is damaged.")));
        final ImportViewModel viewModel = viewModel();

        replacing(viewModel, source, "p9");

        assertThat(projects.removedProjects()).isEmpty();
        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Refused.class);
    }

    @Test
    void openReplacing_newBookRefusedByItsInspection_keepsTheOldProject() {
        final Path source = Path.of("/books/locked.epub");
        projects.on(source, Result.ok(BookFixtures.refused(InspectionVerdict.DRM_PROTECTED, "application/epub+zip")));
        final ImportViewModel viewModel = viewModel();

        replacing(viewModel, source, "p9");

        assertThat(projects.removedProjects()).isEmpty();
    }

    // Importing a file that already has a project reuses that project's id; removing it would delete the new import.
    @Test
    void openReplacing_sameBookReusesTheReplacedProject_removesNothing() {
        final Path source = Path.of("/books/Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = viewModel();

        replacing(viewModel, source, "p1");

        assertThat(projects.removedProjects()).isEmpty();
        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Detected.class);
    }

    private static void replacing(final ImportViewModel viewModel, final Path source, final String replacedId) {
        onFx(() -> {
            viewModel.openReplacing(source, replacedId);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }
}
