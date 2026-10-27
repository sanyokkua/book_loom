package ua.bookloom.ui.dialog;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.ui.state.ProjectRow;

/** The Find the book card, an interface so its caller can be tested against a recording fake. */
public interface FindBookPrompt {

    /**
     * Says the project's book file cannot be used and offers to find it. Escape and Cancel run {@code cancelled}. FX
     * thread only.
     *
     * @param row the row that was being opened
     * @param opening why it did not open: the file is missing or its content changed
     * @param error a refusal to show (the chosen file was another book), or {@code null}
     * @param choose what "Find the book…" does
     * @param cancelled what Cancel or Escape does
     */
    void ask(ProjectRow row, ProjectOpening opening, @Nullable String error, Runnable choose, Runnable cancelled);
}
