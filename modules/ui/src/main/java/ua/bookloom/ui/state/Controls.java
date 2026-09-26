package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * Which of the dashboard's run controls each run state offers.
 *
 * <p>The table, which the screen and the view model both follow:
 *
 * <ul>
 *   <li>{@code IDLE}: start only.
 *   <li>{@code RUNNING}: pause and stop.
 *   <li>{@code PAUSING}: pause unavailable, stop available, no resume.
 *   <li>{@code PAUSED}: resume and stop.
 *   <li>{@code STOPPING}: stop unavailable and nothing else.
 *   <li>{@code STOPPED}, {@code COMPLETED}, {@code FAILED}: a new run only; a stopped run is terminal in this build, so
 *       none of them offers a resume.
 * </ul>
 *
 * <p>Known gap, not a bug: the engine honours no pause during export, so a pause pressed then is ignored by
 * {@link RunSession}, and the pause control stays offered.
 *
 * <p>Start and new run are the same action on two labels: start before any run, new run after one has ended. Only they
 * are affected by {@code preparing}: a run that is already under way needs its pause and stop at once.
 *
 * @param start begins the first run
 * @param newRun begins another run after one has ended
 * @param pause asks the engine to pause at its next boundary
 * @param resume asks a paused engine to continue
 * @param stop asks the run to end
 */
public record Controls(
        ControlState start, ControlState newRun, ControlState pause, ControlState resume, ControlState stop) {

    /** Rejects a missing control state. */
    public Controls {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(newRun, "newRun");
        Objects.requireNonNull(pause, "pause");
        Objects.requireNonNull(resume, "resume");
        Objects.requireNonNull(stop, "stop");
    }

    /**
     * The controls for a run state.
     *
     * @param state where the run is
     * @param preparing whether a start is still building its model and job, which makes start and new run unavailable
     * @return the controls; never null
     */
    public static Controls of(final RunState state, final boolean preparing) {
        Objects.requireNonNull(state, "state");
        final ControlState begin = preparing ? ControlState.DISABLED : ControlState.ENABLED;
        return switch (state) {
            case IDLE -> new Controls(begin, hidden(), hidden(), hidden(), hidden());
            case RUNNING -> new Controls(hidden(), hidden(), ControlState.ENABLED, hidden(), ControlState.ENABLED);
            case PAUSING -> new Controls(hidden(), hidden(), ControlState.DISABLED, hidden(), ControlState.ENABLED);
            case PAUSED -> new Controls(hidden(), hidden(), hidden(), ControlState.ENABLED, ControlState.ENABLED);
            case STOPPING -> new Controls(hidden(), hidden(), hidden(), hidden(), ControlState.DISABLED);
            case STOPPED, COMPLETED, FAILED -> new Controls(hidden(), begin, hidden(), hidden(), hidden());
        };
    }

    private static ControlState hidden() {
        return ControlState.HIDDEN;
    }
}
