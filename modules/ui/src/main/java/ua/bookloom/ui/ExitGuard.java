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

/**
 * Decides whether the window may close: at once when nothing runs, otherwise only after the person confirmed that
 * the run and the other work are stopped with it. Nothing survives a restart yet, so a close that did not ask would lose
 * the work without a word. FX thread only.
 */
@Slf4j
@Singleton
public final class ExitGuard {

    private static final Set<RunState> RUN_UNDER_WAY =
            Set.of(RunState.RUNNING, RunState.PAUSING, RunState.PAUSED, RunState.STOPPING);

    private final ActivityTracker activities;
    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final ConfirmDialog confirm;

    /**
     * Wires the guard to what runs and to the question it asks.
     *
     * @param activities the work under way, which is stopped on confirmation
     * @param mirror where the run's state is read
     * @param runner what stops the run
     * @param confirm the question put to the person
     */
    @Inject
    public ExitGuard(
            final ActivityTracker activities,
            final StateMirror mirror,
            final TranslationRunner runner,
            final ConfirmDialog confirm) {
        this.activities = Objects.requireNonNull(activities, "activities");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
    }

    /**
     * Answers a request to close the window.
     *
     * @param exit what closes the window; runs only after the person confirmed stopping the work
     * @return {@code true} if the window may close now, {@code false} if a question was asked instead and {@code exit}
     *     follows from the answer
     */
    public boolean requestClose(final Runnable exit) {
        Objects.requireNonNull(exit, "exit");
        final RunState state = mirror.runState().get();
        final boolean working = !activities.running().isEmpty() || RUN_UNDER_WAY.contains(state);
        log.debug(
                "close requested: run {}, activities {}, working {}",
                state,
                activities.running().size(),
                working);
        if (!working) {
            return true;
        }
        confirm.ask(ConfirmDialog.Question.QUIT, () -> {
            log.info("closing with work under way: stopping it first");
            stopEverything(state);
            exit.run();
        });
        return false;
    }

    private void stopEverything(final RunState state) {
        activities.stopWhere(kind -> kind != ActivityKind.TRANSLATION);
        if (RUN_UNDER_WAY.contains(state)) {
            runner.cancel();
        }
    }
}
