package ua.bookloom.ui;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Screen;
import ua.bookloom.ui.dialog.ExportCompleteDialog;
import ua.bookloom.ui.dialog.ReplaceRunPrompt;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.theme.ThemeController;

/**
 * Shows one {@link ConformanceCases.Screen} in the real shell, for every test that reads a screen's resolved look.
 *
 * <p>The cases of one class read the same screen many times in a row — part after part, block after block — and
 * none of them changes it, so a case that shows the screen the previous case of the same class showed reads that
 * shell instead of building the graph and preparing the screen again (which was most of these suites' time). A screen
 * with a dialog over it is always rebuilt: TestFX hides every window after each test.
 */
abstract class ConformanceTestBase extends ShellTestBase {

    private static @Nullable Showing lastShown;

    private final ScriptedProjectService projects = new ScriptedProjectService();
    private final ScriptedGlossaryService glossary = new ScriptedGlossaryService();

    private @Nullable Stage stage;
    private boolean adopted;

    /** The shell a case left showing, with the class and screen it was built for. */
    private record Showing(
            Class<?> owner,
            Screen screen,
            Injector injector,
            AppShellView shell,
            Navigator navigator,
            ThemeController themeController,
            Scene scene) {}

    @Override
    public void start(final Stage primary) {
        stage = primary;
        final Showing previous = lastShown;
        adopted = previous != null && previous.owner() == getClass();
        if (previous == null || !adopted) {
            super.start(primary);
            return;
        }
        injector = previous.injector();
        shell = previous.shell();
        navigator = previous.navigator();
        themeController = previous.themeController();
        scene = previous.scene();
        primary.setScene(scene);
        primary.show();
        primary.sizeToScene();
    }

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .glossary(glossary)
                .build();
    }

    protected final void show(final Screen screen) throws TimeoutException {
        final Showing previous = lastShown;
        if (adopted && previous != null && previous.screen().equals(screen) && screen.overlay() == Overlay.NONE) {
            return;
        }
        if (adopted) {
            final Stage primary = Objects.requireNonNull(stage, "stage");
            interact(() -> super.start(primary));
        }
        lastShown = null;
        prepare(screen);
        lastShown = new Showing(getClass(), screen, injector, shell, navigator, themeController, scene);
    }

    private void prepare(final Screen screen) throws TimeoutException {
        final ViewNames view = screen.view();
        if (view != null) {
            onFx(() -> shell.activate(view));
        }
        new ConformancePreparations(injector, projects, glossary).prepare(screen.preparation());
        switch (screen.overlay()) {
            case NONE -> {
                // nothing is open over the shell
            }
            case ABOUT -> onFx(() -> ((Button) required("shell-about")).fire());
            case ERROR_DIALOG ->
                onFx(() -> injector.getInstance(ErrorPresenter.class)
                        .present(AppError.of(
                                ErrorCode.timeout, "Translation failed", "The provider did not answer in time.")));
            case REVIEW_PANEL -> onFx(() -> ((Button) required("translating-review-flagged")).fire());
            case ADD_TERM -> onFx(() -> ((Button) required("names-style-add")).fire());
            case RETRY ->
                onFx(() -> injector.getInstance(RetryWithNoteDialog.class).ask("ch5 · p12", choice -> {}));
            case EXPORT_COMPLETE ->
                onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(finishedExport()));
            case REPLACE_RUN ->
                onFx(() -> injector.getInstance(ReplaceRunPrompt.class)
                        .ask("Frankenstein.epub", "Dracula.epub", RunState.RUNNING, () -> {}));
        }
    }

    // A partial export, so the card's untranslated-segments line is checked in both themes with the rest.
    private static ExportOutcome finishedExport() {
        final Path book = Path.of("/books/Frankenstein.uk.epub");
        return new ExportOutcome(
                new ExportReport(book, 10, 3, 0, 0, 8, 2, List.of(), 10, ConsistencySummary.NOT_RUN, 0), 958_464);
    }
}
