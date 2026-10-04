package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProjectService;

/**
 * What the book-brief view model tests share: a real {@link ImportViewModel} through which books are opened over the
 * scripted project service, and a brief view model whose saves wait in a queue until the test runs them, so that a
 * test can see both what was saved and on which thread.
 */
abstract class BookBriefViewModelTestBase extends FxTestBase {

    static final Path BOOK = Path.of("/books/Frankenstein.epub");

    ScriptedProjectService projects;
    CurrentProject current;
    ImportViewModel imports;
    QueuedExecutor saves;
    BookBriefViewModel brief;

    @Override
    public final void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    final void setUpViewModels() {
        projects = new ScriptedProjectService();
        current = new CurrentProject();
        saves = new QueuedExecutor();
        imports = onFx(() -> new ImportViewModel(
                projects, current, new RecordingToasts(), new RecordingErrorPresenter(), new DirectExecutor()));
        brief = onFx(() -> new BookBriefViewModel(current, projects, saves, tag -> !"xx".equals(tag)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Replaces the view model with a new one over the same current project, as a later first visit would. */
    void recreateBrief() {
        brief = onFx(() -> new BookBriefViewModel(current, projects, saves, tag -> !"xx".equals(tag)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    void openBook(final ImportedBook imported) {
        projects.on(BOOK, Result.ok(imported));
        onFx(() -> {
            imports.open(BOOK);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Runs the queued saves on the calling (JUnit) thread and lets the FX queue publish their completion. */
    void runSaves() {
        saves.runAll();
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Runs {@code change} on the FX thread, as a control would, and waits for the FX queue to drain. */
    void change(final Runnable change) {
        onFx(() -> {
            change.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    BookBrief current() {
        return onFx(() -> current.brief().get());
    }

    boolean canContinue() {
        return onFx(() -> brief.canContinue().get());
    }

    boolean sameLanguage() {
        return onFx(() -> brief.sameLanguage().get());
    }

    boolean sourceUndeclared() {
        return onFx(() -> brief.sourceUndeclared().get());
    }
}
