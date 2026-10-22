package ua.bookloom.ui;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.ui.state.ImportGuard;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;

/** The import page's two states that need more than an answered import: a held read and a book already stored. */
final class ImportPreparations {

    private static final long WAIT_SECONDS = 10;

    private final Injector injector;
    private final ScriptedProjectService projects;

    ImportPreparations(final Injector injector, final ScriptedProjectService projects) {
        this.injector = injector;
        this.projects = projects;
    }

    // The service is held, so the page stays on "Reading…" with its bar and Cancel.
    void readABook() throws TimeoutException {
        final Path source = Path.of("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        projects.hold();
        final ImportViewModel viewModel = injector.getInstance(ImportViewModel.class);
        onFx(() -> {
            viewModel.open(source);
            return null;
        });
        awaitState(ImportState.Opening.class);
    }

    // A file whose bytes hash to the stored projects' content: the page names them instead of importing again.
    void dropAKnownBook() throws TimeoutException {
        final Path file;
        try {
            file = Files.write(Files.createTempFile("known-book", ".epub"), new byte[] {'a', 'b', 'c'});
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        projects.willList(
                List.of(knownProject("p1", "Earth Gravity · EN → UK"), knownProject("p2", "Earth Gravity · EN → PL")));
        onFx(() -> {
            injector.getInstance(ImportGuard.class).requestImport(file);
            return null;
        });
        awaitState(ImportState.AlreadyInProjects.class);
        try {
            Files.deleteIfExists(file);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    private static ProjectSummary knownProject(final String id, final String name) {
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        id,
                        "earth.epub",
                        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                        "en",
                        "uk",
                        ua.bookloom.api.project.NameOrigin.AUTO,
                        name,
                        UiTestInjector.NOW),
                3,
                null,
                null);
    }

    private void awaitState(final Class<? extends ImportState> expected) throws TimeoutException {
        final ImportViewModel viewModel = injector.getInstance(ImportViewModel.class);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> expected.isInstance(viewModel.state().get())));
        WaitForAsyncUtils.waitForFxEvents();
    }
}
