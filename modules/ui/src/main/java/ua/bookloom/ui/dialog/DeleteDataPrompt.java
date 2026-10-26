package ua.bookloom.ui.dialog;

/**
 * The question asked before all the stored data is deleted. An interface so Delete all data can be tested against a
 * recording fake instead of a scene.
 */
public interface DeleteDataPrompt {

    /**
     * Asks whether to delete all data, offering a backup first. Cancel, Escape and Enter change nothing. FX thread
     * only.
     *
     * @param stopsRun whether a translation is running or paused, so the question says it is stopped first
     * @param backupFirst what Make a backup first does, after the card has closed
     * @param delete what the confirming button does, after the card has closed
     */
    void ask(boolean stopsRun, Runnable backupFirst, Runnable delete);
}
