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
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportGuard;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.OpenedBookFixture;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.TechnicalDetails;

/**
 * The import page's two states that need more than an answered import — a held read and a book already stored — and
 * the Ukrainian target the Book Brief's extra rows need.
 */
final class ImportPreparations {

    private static final long WAIT_SECONDS = 10;

    private final Injector injector;
    private final ScriptedProjectService projects;

    ImportPreparations(final Injector injector, final ScriptedProjectService projects) {
        this.injector = injector;
        this.projects = projects;
    }

    /** Turns Show technical details on, which the group warning and the round-trip lines need to be shown. */
    void showTechnicalLines() {
        onFx(() -> {
            injector.getInstance(TechnicalDetails.class).property().set(true);
            return null;
        });
    }

    /** Chooses the model whose name carries a quant, and waits for the ready card's chip. */
    void chooseAModelWithAGoodQuant() throws TimeoutException {
        onFx(() -> {
            injector.getInstance(SettingsViewModel.class).model().set(ConformanceTestBase.QUANT_MODEL);
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() ->
                        injector.getInstance(Navigator.class).content().get().lookup("#quant-chip") != null));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Adopts the model whose maker's list leaves Ukrainian out, and waits for the ready card's warning. */
    void chooseAModelWithALanguageGap() throws TimeoutException {
        final SettingsViewModel settings = injector.getInstance(SettingsViewModel.class);
        onFx(() -> {
            OpenedBookFixture.open(injector.getInstance(CurrentProject.class), "ru", "uk");
            settings.reloadProviders();
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> !settings.providers().isEmpty()));
        onFx(() -> {
            settings.adopt(new ModelSelection("ollama", ConformanceTestBase.GAP_MODEL));
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> {
                    final Node line = injector.getInstance(Navigator.class)
                            .content()
                            .get()
                            .lookup("#translating-ready-language-line");
                    return line instanceof Labeled labelled
                            && !labelled.getText().isEmpty();
                }));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens a book into Ukrainian, whose language file gives the brief its extra rows. */
    void openIntoUkrainian() throws TimeoutException {
        final Path source = Path.of("/books/Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        onFx(() -> {
            injector.getInstance(ImportViewModel.class).open(source);
            return null;
        });
        awaitState(ImportState.Detected.class);
        onFx(() -> {
            injector.getInstance(BookBriefViewModel.class).setTargetLanguage("uk");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // The service is held, so the page stays on "Reading..." with its bar and Cancel.
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
