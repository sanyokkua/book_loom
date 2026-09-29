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

    /** Closes the question once the run has ended; ignored when it is no longer shown. */
    void dismiss();
}
