package ua.bookloom.api.pipeline;

/** Receives an export's progress on the thread that runs it, so an implementation hands off to its own thread. */
@FunctionalInterface
public interface ExportProgressListener {

    /** A listener that ignores every announcement. */
    ExportProgressListener NONE = progress -> {};

    /**
     * Reports a step reached or a unit finished.
     *
     * @param progress the non-null progress
     */
    void onProgress(ExportProgress progress);

    /**
     * Shows a model call of the consistency pass as it now stands, the way a run announces its calls: once when it
     * goes out and again as it waits, ends and gathers outcomes, always under one call id. The snapshot carries book
     * text and the reply, so a listener keeps them in memory and never logs them above TRACE. A listener that shows
     * no calls ignores it.
     *
     * @param snapshot the non-null call
     */
    default void onCall(final CallSnapshot snapshot) {
        // Shows no calls.
    }
}
