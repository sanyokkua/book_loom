package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * Opens a stored project from the Projects list or at launch: the service reads it off the FX thread, the window then
 * adopts its book and shows the step the row names. It never starts a run; that stays the person's press of Start.
 */
@Slf4j
@Singleton
public final class ProjectOpener {

    private final ProjectService projects;
    private final ImportViewModel importer;
    private final Navigator navigator;
    private final StepLocks locks;
    private final ProjectActions actions;
    private final ErrorPresenter errors;

    private final ExecutorService executor;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param projects the service a stored project is opened through
     * @param importer what makes the opened book the window's open one
     * @param navigator where the step is shown
     * @param locks which steps are still locked once the book is open
     * @param actions where a missing or changed book file is handed
     * @param errors where a failure to open is shown
     * @param executor the daemon executor the service runs on, never the FX thread
     */
    @Inject
    public ProjectOpener(
            final ProjectService projects,
            final ImportViewModel importer,
            final Navigator navigator,
            final StepLocks locks,
            final ProjectActions actions,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.importer = Objects.requireNonNull(importer, "importer");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Opens the project of a row. Returns at once; the answer is handled on the FX thread. FX thread only.
     *
     * @param row the row to open
     */
    public void open(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        final String id = row.summary().project().id();
        log.info("opening project {} at {}", id, row.step());
        log.trace("opening the project of {}", row.summary().project().source());
        executor.execute(() -> openOffThread(row, id));
    }

    private void openOffThread(final ProjectRow row, final String id) {
        log.debug("opening project {} on {}", id, Thread.currentThread().getName());
        Result<ProjectOpening> answer;
        try {
            answer = projects.open(id);
        } catch (Throwable thrown) {
            log.error("the project service threw instead of returning a result while opening {}", id, thrown);
            answer = Result.err(AppError.of(
                    ErrorCode.internal,
                    "Unexpected error",
                    "The project could not be opened because of an unexpected error.",
                    null,
                    thrown));
        }
        final Result<ProjectOpening> published = answer;
        Platform.runLater(() -> publish(row, published));
    }

    private void publish(final ProjectRow row, final Result<ProjectOpening> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            log.warn("project {} did not open: {}", row.summary().project().id(), failure.code());
            errors.present(failure);
            return;
        }
        final ProjectOpening opening = Objects.requireNonNull(answer.data(), "opening");
        switch (opening) {
            case ProjectOpening.Opened opened -> adopt(row, opened);
            case ProjectOpening.BookFileMissing missing -> findBook(row, missing);
            case ProjectOpening.BookFileChanged changed -> findBook(row, changed);
        }
    }

    private void findBook(final ProjectRow row, final ProjectOpening opening) {
        log.warn(
                "the book file of project {} cannot be used: {}",
                row.summary().project().id(),
                opening.getClass().getSimpleName());
        actions.findBook(row, opening);
    }

    private void adopt(final ProjectRow row, final ProjectOpening.Opened opened) {
        final String id = row.summary().project().id();
        importer.adopt(row.summary().project().source(), opened.book());
        log.info("opened project {}, interrupted run: {}", id, opened.interrupted());
        final ViewNames step = locks.lockReason(row.step()).isPresent() ? ViewNames.BOOK_BRIEF : row.step();
        log.debug("step for {} is {}", id, step);
        navigator.navigate(step);
    }
}
