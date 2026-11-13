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
}
