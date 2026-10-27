package ua.bookloom.ui.dialog;

import java.util.List;
import java.util.function.Consumer;

/**
 * The question asked after the first-launch look found several models: which one to use. An interface so the look can
 * be tested against a recording fake instead of a scene.
 */
public interface ModelPickPrompt {

    /**
     * Asks which of the models to use. Escape means "Not now". FX thread only.
     *
     * @param providerId the id of the program that answered
     * @param modelIds the models it offers, in the order it listed them; at least two
     * @param use what to do with the one model the person picked; not run on "Not now"
     * @param notNow what to do when the person declines to pick
     */
    void ask(String providerId, List<String> modelIds, Consumer<String> use, Runnable notNow);
}
