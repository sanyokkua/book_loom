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
 *   <li>{@code PAUSED}: resume and stop, and no start, so a second run never begins beside the one that can be resumed.
 *   <li>{@code STOPPING}: stop unavailable and nothing else.
 *   <li>{@code STOPPED}: resume only, which starts a new job at the first pending segment.
 *   <li>{@code COMPLETED}: start only while segments are still pending, otherwise nothing.
 *   <li>{@code FAILED}: start.
 * </ul>
 *
 * <p>Known gap, not a bug: the engine honours no pause during export, so a pause pressed then is ignored by
 * {@link RunSession}, and the pause control stays offered.
 *
 * <p>Only start and the resume of a stopped run, which also builds a job, are affected by {@code preparing}: a run that
 * is already under way needs its pause and stop at once.
 *
 * @param start begins a run, the first or another after one has ended
 * @param pause asks the engine to pause at its next boundary
 * @param resume asks a paused engine to continue
 * @param stop asks the run to end
 */
public record Controls(ControlState start, ControlState pause, ControlState resume, ControlState stop) {

    /** Rejects a missing control state. */
    public Controls {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(pause, "pause");
        Objects.requireNonNull(resume, "resume");
        Objects.requireNonNull(stop, "stop");
    }

    /**
     * The controls for a run state.
     *
     * @param state where the run is
     * @param preparing whether a start is still building its model and job, which makes start and a stopped run's
     *     resume unavailable
     * @param pendingRemain whether the project still holds pending segments, which a completed run needs to offer a
     *     start
     * @return the controls; never null
     */
    public static Controls of(final RunState state, final boolean preparing, final boolean pendingRemain) {
        Objects.requireNonNull(state, "state");
        final ControlState begin = preparing ? ControlState.DISABLED : ControlState.ENABLED;
        return switch (state) {
            case IDLE, FAILED -> new Controls(begin, hidden(), hidden(), hidden());
            case RUNNING -> new Controls(hidden(), ControlState.ENABLED, hidden(), ControlState.ENABLED);
            case PAUSING -> new Controls(hidden(), ControlState.DISABLED, hidden(), ControlState.ENABLED);
            case PAUSED -> new Controls(hidden(), hidden(), ControlState.ENABLED, ControlState.ENABLED);
            case STOPPING -> new Controls(hidden(), hidden(), hidden(), ControlState.DISABLED);
            case STOPPED -> new Controls(hidden(), hidden(), begin, hidden());
            case COMPLETED -> new Controls(pendingRemain ? begin : hidden(), hidden(), hidden(), hidden());
        };
    }

    private static ControlState hidden() {
        return ControlState.HIDDEN;
    }
}
