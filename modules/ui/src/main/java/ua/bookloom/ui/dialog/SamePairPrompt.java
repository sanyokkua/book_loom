package ua.bookloom.ui.dialog;

import java.util.function.Consumer;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/** The Same language pair card, an interface so its callers can be tested against a recording fake. */
public interface SamePairPrompt {

    /**
     * Offers to open the project that holds the language pair or to make a second one under a name of its own.
     * Escape and the keep button run {@code cancelled}. FX thread only.
     *
     * @param existing the row of the project that holds the pair
     * @param languageName the target language's name in the interface language
     * @param keepName the caption of the cancelling button
     * @param rules what the typed name is judged against
     * @param openExisting what "Open that project" does
     * @param makeSecond what the name button does with the typed name
     * @param cancelled what the cancelling button or Escape does
     */
    void ask(
            ProjectRow existing,
            String languageName,
            String keepName,
            ProjectNameRules rules,
            Runnable openExisting,
            Consumer<String> makeSecond,
            Runnable cancelled);
}
