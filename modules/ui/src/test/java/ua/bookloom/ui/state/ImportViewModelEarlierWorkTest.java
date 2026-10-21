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
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedProjectService;

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

    // IF start over left the earlier project behind, THEN two projects would hold one file and the window could not
    // tell them apart.
    @Test
    void open_startOverWithAnEarlierProject_removesItBeforeCreatingAndNeverOpens() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(
                source, Result.ok(BookFixtures.imported("p1", BookFormat.EPUB, "en", "Frankenstein", "Shelley", 3)));
        projects.onDecided(source, 4);
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(projects.events()).containsExactly("remove id:" + source, "import " + source);
        assertThat(projects.opens()).isEmpty();
    }

    @Test
    void open_noEarlierProject_createsWithoutRemovingAnything() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(
                source, Result.ok(BookFixtures.imported("p1", BookFormat.EPUB, "en", "Frankenstein", "Shelley", 3)));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(projects.events()).containsExactly("import " + source);
        assertThat(projects.removedProjects()).isEmpty();
    }

    // The earlier project's book file can be gone; that reads as there being nothing to continue, never as a crash.
    @Test
    void continueWith_bookFileMissing_reportsValidationAndOpensNothing() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.onDecided(source, 4);
        projects.onOpen(
                source, Result.ok(new ProjectOpening.BookFileMissing(ScriptedProjectService.storedProjectOf(source))));
        final ImportViewModel viewModel = viewModel();

        continueWith(viewModel, source);

        assertThat(stateOf(viewModel))
                .isInstanceOfSatisfying(
                        ImportState.Refused.class,
                        refused -> assertThat(refused.error().code()).isEqualTo(ErrorCode.validation));
        assertThat(openedBook()).isNull();
    }

    @Test
    void continueWith_bookFileChanged_reportsValidationAndOpensNothing() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.onDecided(source, 4);
        projects.onOpen(
                source, Result.ok(new ProjectOpening.BookFileChanged(ScriptedProjectService.storedProjectOf(source))));
        final ImportViewModel viewModel = viewModel();

        continueWith(viewModel, source);

        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Refused.class);
        assertThat(openedBook()).isNull();
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
