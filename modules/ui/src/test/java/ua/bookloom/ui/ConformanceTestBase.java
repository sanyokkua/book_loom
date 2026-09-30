package ua.bookloom.ui;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.ui.ConformanceCases.Screen;
import ua.bookloom.ui.dialog.ExportCompleteDialog;
import ua.bookloom.ui.dialog.ReplaceRunPrompt;
import ua.bookloom.ui.dialog.RetryWithNoteDialog;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.RunState;

/** Shows one {@link ConformanceCases.Screen} in the real shell, for every test that reads a screen's resolved look. */
abstract class ConformanceTestBase extends ShellTestBase {

    private final ScriptedProjectService projects = new ScriptedProjectService();
    private final ScriptedGlossaryService glossary = new ScriptedGlossaryService();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .glossary(glossary)
                .build();
    }

    protected final void show(final Screen screen) throws TimeoutException {
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
                onFx(() -> injector.getInstance(ExportCompleteDialog.class)
                        .show(new ExportOutcome(
                                new ExportReport(
                                        Path.of("/books/Frankenstein.uk.epub"), 10, 0, 0, 0, 8, 2, List.of(), 10),
                                958_464)));
            case REPLACE_RUN ->
                onFx(() -> injector.getInstance(ReplaceRunPrompt.class)
                        .ask("Frankenstein.epub", "Dracula.epub", RunState.RUNNING, () -> {}));
        }
    }
}
