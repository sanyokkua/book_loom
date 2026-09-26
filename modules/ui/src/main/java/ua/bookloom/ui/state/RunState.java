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
    FAILED
}
