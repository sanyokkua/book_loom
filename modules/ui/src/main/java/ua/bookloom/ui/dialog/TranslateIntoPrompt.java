package ua.bookloom.ui.dialog;

import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.ProjectNameRules;

/** The Translate into another language card, an interface so its caller can be tested against a recording fake. */
public interface TranslateIntoPrompt {

    /**
     * Asks for the target language and, where needed, a name. Escape and Cancel run {@code cancelled}. FX thread only.
     *
     * @param group the book
     * @param rules what a typed name is judged against
     * @param create what Create does with the target tag and the typed name, {@code null} for the automatic name
     * @param cancelled what Cancel or Escape does
     */
    void ask(BookGroup group, ProjectNameRules rules, BiConsumer<String, @Nullable String> create, Runnable cancelled);
}
