package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;

/** Opening a file keeps or replaces its stored project: the view model looks the project up by id first. */
class ImportViewModelEarlierWorkTest extends ImportViewModelTestBase {

    @TempDir
    private Path dir;

    private void continueWith(final ImportViewModel viewModel, final Path source) {
        onFx(() -> {
            viewModel.continueWith(source);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF Continue created the project again, THEN the stored decisions the question promised would be reset.
    @Test
    void continueWith_earlierProjectStored_listsThenOpensByIdAndNeverCreates() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        projects.onDecided(source, 4);
        final ImportViewModel viewModel = viewModel();

        continueWith(viewModel, source);

        assertThat(projects.listCalls()).isEqualTo(1);
        assertThat(projects.opens()).containsExactly(source);
        assertThat(projects.imports()).isEmpty();
        assertThat(openedBook()).isNotNull();
    }

    @Test
    void open_startOver_createsAndNeverListsOrOpens() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(
                source, Result.ok(BookFixtures.imported("p1", BookFormat.EPUB, "en", "Frankenstein", "Shelley", 3)));
        projects.onDecided(source, 4);
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(projects.imports()).containsExactly(source);
        assertThat(projects.listCalls()).isZero();
        assertThat(projects.opens()).isEmpty();
    }

    // The earlier project can vanish between the question and the click; that is a validation error, not a new import.
    @Test
    void continueWith_noEarlierProject_reportsValidationAndCreatesNothing() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = viewModel();

        continueWith(viewModel, source);

        assertThat(stateOf(viewModel))
                .isInstanceOfSatisfying(
                        ImportState.Refused.class,
                        refused -> assertThat(refused.error().code()).isEqualTo(ErrorCode.validation));
        assertThat(projects.imports()).isEmpty();
        assertThat(projects.opens()).isEmpty();
    }
}
