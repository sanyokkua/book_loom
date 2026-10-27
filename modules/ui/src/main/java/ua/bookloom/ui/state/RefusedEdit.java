package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a refused save's reason only while the editor still holds the text that was refused: once the person types
 * anything else, the reason no longer describes what they see, so it goes at once instead of at the next Save. A
 * refusal that arrives after the text was already changed is not shown. FX thread only.
 */
@Slf4j
final class RefusedEdit {

    private final ReviewEditor editor;
    private final Consumer<@Nullable String> problem;
    private @Nullable String sent;
    private boolean shown;

    RefusedEdit(final ReviewEditor editor, final Consumer<@Nullable String> problem) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.problem = Objects.requireNonNull(problem, "problem");
        editor.text().addListener((observed, was, now) -> typed());
    }

    /**
     * Names the stored text a save sent, or none for any other action.
     *
     * @param stored the stored form of the text sent, or {@code null} when the action is not a save
     */
    void sent(final @Nullable String stored) {
        sent = stored;
        shown = false;
    }

    /** Shows the reason an action was refused, unless it refuses a save whose text the person has typed over. */
    void show(final String reason) {
        Objects.requireNonNull(reason, "reason");
        if (sent != null && !sent.equals(editor.storedText())) {
            log.debug("a refused save's reason is not shown: the editor no longer holds the refused text");
            return;
        }
        shown = sent != null;
        problem.accept(reason);
    }

    // Called on every keystroke, so it logs only when it clears the reason.
    private void typed() {
        if (shown && !Objects.equals(sent, editor.storedText())) {
            log.debug("the refused text was typed over: its reason is cleared");
            shown = false;
            problem.accept(null);
        }
    }
}
