package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslationRunner;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * Decides whether the window may close: at once when nothing would be lost, otherwise only after the person confirmed.
 * Nothing survives a restart yet, so a close that did not ask would lose the work without a word. It asks while a run
 * is under way or work the window waits for runs (that work is stopped first), and while a run that ended was never
 * exported; a model listing or a connection check is no reason to ask. FX thread only.
 */
@Slf4j
@Singleton
public final class ExitGuard {

    private static final Set<RunState> RUN_UNDER_WAY =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPING);
    private static final Set<RunState> RUN_ENDED = Set.of(RunState.STOPPED, RunState.COMPLETED, RunState.FAILED);

    private final ActivityTracker activities;
    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final ConfirmDialog confirm;
    private final WorkflowProgress progress;

    /**
     * Wires the guard to what runs and to the question it asks.
     *
     * @param activities the work under way, which is stopped on confirmation
     * @param mirror where the run's state is read
     * @param runner what stops the run
     * @param confirm the question put to the person
     * @param progress the workflow steps done, where an export of the open book is marked
     */
    @Inject
    public ExitGuard(
            final ActivityTracker activities,
            final StateMirror mirror,
            final TranslationRunner runner,
            final ConfirmDialog confirm,
            final WorkflowProgress progress) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.progress = Objects.requireNonNull(progress, "progress");
    }

    /**
     * Answers a request to close the window.
     *
     * @param exit what closes the window; runs only after the person confirmed
     * @return {@code true} if the window may close now, {@code false} if a question was asked instead and {@code exit}
     *     follows from the answer
     */
    public boolean requestClose(final Runnable exit) {
        Objects.requireNonNull(exit, "exit");
        final RunState state = mirror.runState().get();
        final boolean working = activities.blocking().get() || RUN_UNDER_WAY.contains(state);
        final boolean unexported = RUN_ENDED.contains(state) && !progress.done().contains(ViewNames.EXPORT);
        log.debug(
                "close requested: run {}, activities {}, working {}, unexported {}",
                state,
                activities.running().size(),
                working,
                unexported);
        if (working) {
            confirm.ask(ConfirmDialog.Question.QUIT, () -> {
                log.info("closing with work under way: stopping it first");
                stopEverything(state);
                exit.run();
            });
            return false;
        }
        if (unexported) {
            confirm.ask(ConfirmDialog.Question.QUIT_UNEXPORTED, () -> {
                log.info("closing on a {} run that was never exported", state);
                exit.run();
            });
            return false;
        }
        return true;
    }

    private void stopEverything(final RunState state) {
        activities.stopWhere(kind -> kind != ActivityKind.TRANSLATION);
        if (RUN_UNDER_WAY.contains(state)) {
            runner.cancel();
        }
    }
}
