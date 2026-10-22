package ua.bookloom.ui.dialog;

import ua.bookloom.api.llm.ModelSelection;

/**
 * The two questions asked before a stopped or finished translation continues: the chosen model is not the one the
 * translation was started with, and the model to use does not answer. An interface so the guard can be tested against
 * a recording fake instead of a scene.
 */
public interface ContinuePrompt {

    /**
     * Says the model to use is not answering and offers to check again, to use another model, or to leave it. Escape
     * means "Not now". FX thread only.
     *
     * @param model the program and model that did not answer
     * @param tryAgain what to do when the person asks to check again
     * @param useAnother what to do when the person wants to choose another model
     * @param notNow what to do when the person leaves it
     */
    void askNotAnswering(ModelSelection model, Runnable tryAgain, Runnable useAnother, Runnable notNow);

    /**
     * Says the chosen model differs from the one the translation was started with. Escape runs {@code cancelled} and
     * starts nothing. FX thread only.
     *
     * @param earlier the model the translation was started with
     * @param now the model chosen now
     * @param useEarlier what to do when the person keeps the earlier model
     * @param useNew what to do when the person goes on with the new one
     * @param cancelled what to do when the card is closed without a choice
     */
    void askDifferent(
            ModelSelection earlier, ModelSelection now, Runnable useEarlier, Runnable useNew, Runnable cancelled);
}
