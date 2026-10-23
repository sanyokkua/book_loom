package ua.bookloom.ui.dialog;

/**
 * The question asked when the current AI model is changed while the open book already has translated segments. An
 * interface so the switch can be tested against a recording fake instead of a scene.
 */
public interface SwitchProviderPrompt {

    /**
     * Asks whether to keep the model the book was translated with or use the other one. Escape and Enter keep the
     * earlier one, which changes nothing. FX thread only.
     *
     * @param chosenName the name of the provider that would become current
     * @param earlierName the name of the provider the book has been translated with so far
     * @param earlierModel the model the book has been translated with so far
     * @param use what to do when the person chooses the new provider
     */
    void ask(String chosenName, String earlierName, String earlierModel, Runnable use);
}
