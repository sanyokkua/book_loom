package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
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

    @Test
    void openReplacing_storedProject_removesItThenCreates() {
        final Path source = Path.of("/books/Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = viewModel();

        onFx(() -> {
            viewModel.openReplacing(source, "p9");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.events()).containsSubsequence("remove p9", "import /books/Frankenstein.epub");
    }
}
