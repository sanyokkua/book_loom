package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;

/**
 * Cancel while a book is still being read: the page goes idle at once, and when the service finishes anyway the
 * project it made is removed and its answer is ignored.
 */
class ImportViewModelCancelOpeningTest extends ImportViewModelTestBase {

    @TempDir
    private Path dir;

    private ImportViewModel openAndHold(final Path source) throws InterruptedException {
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();
        projects.hold();
        open(viewModel, source);
        projects.awaitEntered();
        return viewModel;
    }

    private void cancel(final ImportViewModel viewModel) {
        onFx(() -> {
            viewModel.cancel();
            return null;
        });
    }

    // IF the Reading bar's Cancel waited for the answer, THEN the person would watch a bar they already refused.
    @Test
    void cancel_whileOpening_goesIdleAtOnceAndAllowsAnotherOpen() throws InterruptedException {
        final ImportViewModel viewModel = openAndHold(dir.resolve("first.epub"));

        cancel(viewModel);

        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
        assertThat(isOpening(viewModel)).isFalse();
        projects.release();
    }

    // IF the late answer were published, THEN a book the person cancelled would appear as the open project.
    @Test
    void cancel_whileOpening_removesTheProjectTheLateAnswerCreatedAndShowsNothing()
            throws InterruptedException, TimeoutException {
        final ImportViewModel viewModel = openAndHold(dir.resolve("first.epub"));
        cancel(viewModel);

        projects.release();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !projects.removedProjects().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.removedProjects()).containsExactly("p1");
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
        assertThat(openedBook()).isNull();
        assertThat(toasts.raised()).isEmpty();
    }

    // The book that was open before is not what was cancelled: it stays open and is not released.
    @Test
    void cancel_whileOpeningBesideAnOpenBook_keepsTheOpenBook() throws InterruptedException, TimeoutException {
        final Path first = dir.resolve("first.epub");
        final Path second = dir.resolve("second.epub");
        projects.on(first, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(second, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, "en", "Other", "Someone", 1)));
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();
        open(viewModel, first);
        awaitAnswered(viewModel);
        projects.hold();
        open(viewModel, second);
        projects.awaitEntered();

        cancel(viewModel);
        projects.release();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !projects.removedProjects().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.removedProjects()).containsExactly("p2");
        assertThat(projects.closedProjects()).isEmpty();
        assertThat(openedBook()).isNotNull().extracting(OpenedBook::projectId).isEqualTo("p1");
    }

    // IF the late answer named a project stored before the import, THEN cancelling would delete someone's translation.
    @Test
    void cancel_whileOpening_importReusedAStoredProject_keepsIt() throws InterruptedException {
        projects.willList(List.of(ImportGuardTestBase.summary("p1", "Earth Gravity", "any-hash")));
        final ImportViewModel viewModel = openAndHold(dir.resolve("first.epub"));
        cancel(viewModel);

        projects.release();
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.removedProjects()).isEmpty();
        assertThat(openedBook()).isNull();
    }
}
