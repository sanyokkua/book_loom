package ua.bookloom.ui.dialog;

import ua.bookloom.ui.state.RunState;

/**
 * The question asked before an import throws away a translation that can still continue. An interface so the import
 * guard can be tested against a recording fake instead of a scene.
 */
public interface ReplaceRunPrompt {

    /**
     * Asks whether to discard the current translation and import another book. FX thread only.
     *
     * @param runFileName the file the current run translates
     * @param newFileName the file about to be imported
     * @param state the run's state, which decides the wording: a stopped run reads differently from one that is still
     *     going
     * @param onConfirm what to do when the person chooses to discard; not run when they keep the run
     */
    void ask(String runFileName, String newFileName, RunState state, Runnable onConfirm);

    /**
     * Asks whether to continue a book's earlier translation or start over, before an import resets it. Closing the
     * card without choosing (Escape) runs neither action. FX thread only.
     *
     * @param fileName the file about to be imported
     * @param decidedSegments how many of its segments already hold a decision; at least 1
     * @param onContinue what to do when the person keeps the earlier translation
     * @param onStartOver what to do when the person discards it and imports afresh
     */
    void askEarlierWork(String fileName, int decidedSegments, Runnable onContinue, Runnable onStartOver);

    /** Closes the question once the run has ended; ignored when it is no longer shown. */
    void dismiss();
}
