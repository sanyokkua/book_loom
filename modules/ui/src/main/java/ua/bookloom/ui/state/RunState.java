package ua.bookloom.ui.state;

/**
 * Where a run is, as the person should see it.
 *
 * <p>{@link #PAUSING} and {@link #STOPPING} exist apart from {@link #PAUSED} and {@link #STOPPED} because a request
 * is honoured only at the engine's next safe boundary: the screen must say a request is pending rather than claim a
 * state the engine has not reached.
 */
public enum RunState {
    /** No run has started since the mirror was created. */
    IDLE,
    /** A run is translating or exporting. */
    RUNNING,
    /** A pause was requested and the engine has not reached a boundary yet. */
    PAUSING,
    /** The engine reported it is waiting at a boundary. */
    PAUSED,
    /** A stop was requested and the run has not returned yet. */
    STOPPING,
    /** The run returned cancelled; a neutral outcome, not a failure. */
    STOPPED,
    /** The run returned completed and the book is written. */
    COMPLETED,
    /** The run was refused or ended on an error. */
    FAILED;

    /**
     * Whether the run is over, so figures that look ahead or tick with it, such as the time left, no longer apply.
     *
     * @return {@code true} once the run was stopped, completed or failed, {@code false} otherwise
     */
    public boolean hasEnded() {
        return this == STOPPED || this == COMPLETED || this == FAILED;
    }

    /**
     * Whether the run has stored everything it decided, so the review desk's count is complete.
     *
     * @return {@code true} when paused or ended, {@code false} while it decides or before any run
     */
    public boolean isSettled() {
        return hasEnded() || this == PAUSED;
    }
}
