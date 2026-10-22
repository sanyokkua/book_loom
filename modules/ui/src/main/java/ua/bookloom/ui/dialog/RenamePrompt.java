package ua.bookloom.ui.dialog;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/** The Rename card, an interface so the row actions can be tested against a recording fake instead of a scene. */
public interface RenamePrompt {

    /**
     * Asks for a new name. Escape and Cancel run {@code cancelled}. FX thread only.
     *
     * @param row the project's row; its current name is the field's start
     * @param rules what the typed name is judged against
     * @param error a refusal to show before any edit (the service found the name used), or {@code null}
     * @param typed the text to start the field with (what the person typed before a refusal), or {@code null} for the
     *     current name
     * @param rename what Rename does with the typed name, or with {@code null} for "use the automatic name again"
     * @param remove what "Remove project…" does
     * @param cancelled what Cancel or Escape does
     */
    void ask(
            ProjectRow row,
            ProjectNameRules rules,
            @Nullable String error,
            @Nullable String typed,
            Consumer<@Nullable String> rename,
            Runnable remove,
            Runnable cancelled);
}
