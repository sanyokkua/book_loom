package ua.bookloom.ui.dialog;

import ua.bookloom.ui.state.RunState;

/**
 * The question asked when a book is chosen while a project is open. An interface so the import guard can be tested
 * against a recording fake instead of a scene.
 */
public interface OpenAnotherBookPrompt {

    /**
     * Asks whether to keep the open project and open the book as a new one, or to replace the project. Closing the card
     * with Cancel or Escape runs neither action. FX thread only.
     *
     * @param openProjectName the display name of the project that is open
     * @param newFileName the file about to be opened
     * @param state the run's state; a running run is stopped before the answer is carried out, which the card shows
     * @param onOpenNew what to do when the person keeps the project and opens the book beside it
     * @param onReplace what to do when the person deletes the project's translation and opens the book instead
     */
    void ask(String openProjectName, String newFileName, RunState state, Runnable onOpenNew, Runnable onReplace);

    /** Closes the question once the answer is carried out; ignored when it is no longer shown. */
    void dismiss();
}
