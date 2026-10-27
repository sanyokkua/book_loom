package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * Opens a stored project from the Projects list or at launch: the service reads it off the FX thread, the window then
 * adopts its book and shows the step the row names. It never starts a run; that stays the person's press of Start.
 *
 * <p>The window shows one run, and a run belongs to the project it translates. Opening another project leaves a run
 * that has ended behind, so the opened project shows its own state; a run still at work (running, pausing, paused or
 * stopping) is stopped first, once the person agrees, because one translation exists per application run and the
 * stopped one can be continued later from Projects.
 */
@Slf4j
@Singleton
public final class ProjectOpener implements RowOpener {

    private final ProjectService projects;
    private final ImportViewModel importer;
    private final Navigator navigator;
    private final StepLocks locks;
    private final ProjectActions actions;
    private final ErrorPresenter errors;
    private final RunSwitch runs;

    private final OffThread calls;
    // The runs this window found cut off when it opened their project; the store keeps them as stopped. FX thread only.
    private final Set<String> interruptedRuns = new HashSet<>();

    /**
     * What opening another project does to the window's run.
     *
     * @param mirror the run's state and project
     * @param runner what stops a run still at work
     * @param confirm the question asked before it is stopped
     */
    public record RunSwitch(StateMirror mirror, TranslationRunner runner, ConfirmPrompt confirm) {

        /** Receives the three from the injector. */
        @Inject
        public RunSwitch {
            Objects.requireNonNull(mirror, "mirror");
            Objects.requireNonNull(runner, "runner");
            Objects.requireNonNull(confirm, "confirm");
        }
    }

    /**
     * Receives the collaborators the injector owns.
     *
     * @param projects the service a stored project is opened through
     * @param importer what makes the opened book the window's open one
     * @param navigator where the step is shown
     * @param locks which steps are still locked once the book is open
     * @param actions where a missing or changed book file is handed
     * @param errors where a failure to open is shown
     * @param runs what opening another project does to the window's run
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
            final RunSwitch runs,
            @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.importer = Objects.requireNonNull(importer, "importer");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.calls = new OffThread(Objects.requireNonNull(executor, "executor"));
    }

    /**
     * Opens the project of a row. Returns at once; the answer is handled on the FX thread. FX thread only.
     *
     * @param row the row to open
     */
    @Override
    public void open(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        final String id = row.summary().project().id();
        log.info("opening project {} at {}", id, row.step());
        log.trace("opening the project of {}", row.summary().project().source());
        final StateMirror mirror = runs.mirror();
        final RunState state = mirror.runState().get();
        final boolean another = isAnothersRun(id);
        log.debug("the window's run is {}, of another project {}", state, another);
        if (another && !state.isIdleOrEnded()) {
            askToStop(row, id);
            return;
        }
        openOffThread(row, id);
    }

    /**
     * Whether a project's stored latest run was cut off by the application closing: one this window found so when it
     * opened the project, or one stored as running or paused that this window is not at work on. FX thread only.
     *
     * @param run the stored latest run
     * @return {@code true} if it was interrupted, {@code false} otherwise
     */
    public boolean isInterrupted(final RunRecord run) {
        Objects.requireNonNull(run, "run");
        if (interruptedRuns.contains(run.runId())) {
            return true;
        }
        final boolean open = run.state() == JobState.RUNNING || run.state() == JobState.PAUSED;
        final StateMirror mirror = runs.mirror();
        final boolean live = !mirror.runState().get().isIdleOrEnded()
                && run.projectId().equals(mirror.runProjectId().get());
        return open && !live;
    }

    // Whether the window holds a run, at work or ended, of a project other than this one.
    private boolean isAnothersRun(final String id) {
        final StateMirror mirror = runs.mirror();
        final String runOf = mirror.runProjectId().get();
        return mirror.runState().get() != RunState.IDLE && runOf != null && !runOf.equals(id);
    }

    private void askToStop(final ProjectRow row, final String id) {
        final String book =
                Objects.requireNonNullElse(runs.mirror().runFileName().get(), "");
        log.info("project {} waits for an answer: another project's translation is at work", id);
        runs.confirm()
                .ask(
                        ConfirmPrompt.Question.SWITCH_PROJECT,
                        List.of(book, row.summary().project().name()),
                        () -> stopThenOpen(row, id));
    }

    private void stopThenOpen(final ProjectRow row, final String id) {
        log.info(
                "stopping the translation of {} before project {} opens",
                runs.mirror().runProjectId().get(),
                id);
        RunWaits.when(runs.mirror(), RunState::isIdleOrEnded, () -> openOffThread(row, id));
        runs.runner().cancel();
    }

    private void openOffThread(final ProjectRow row, final String id) {
        calls.call(
                "open project " + id,
                "The project could not be opened because of an unexpected error.",
                () -> projects.open(id),
                answer -> publish(row, answer));
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
        final boolean leftBehind =
                isAnothersRun(id) && runs.mirror().runState().get().isIdleOrEnded();
        importer.adopt(row.summary().project().source(), opened.book());
        if (leftBehind) {
            log.info("the ended run of another project is left behind: project {} shows its own state", id);
            runs.mirror().publishRunCleared();
        }
        log.info("opened project {}, interrupted run: {}", id, opened.interrupted());
        final RunRecord cutOff = row.summary().latestRun();
        if (opened.interrupted() && cutOff != null) {
            interruptedRuns.add(cutOff.runId());
        }
        final ViewNames step = locks.lockReason(row.step()).isPresent() ? ViewNames.BOOK_BRIEF : row.step();
        log.debug("step for {} is {}", id, step);
        navigator.navigate(step);
    }
}
