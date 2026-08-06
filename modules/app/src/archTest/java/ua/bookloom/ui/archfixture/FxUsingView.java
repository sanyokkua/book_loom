package ua.bookloom.ui.archfixture;

import javafx.scene.control.Label;

/**
 * NEGATIVE control for {@code fx-free-core}: JavaFX inside {@code :ui}, which the rule must NOT flag.
 *
 * <p>{@code :ui} and {@code :app} are the two modules that legitimately see a toolkit. A rule scoped to
 * {@code ua.bookloom..} instead of the six core modules would reject this and make the UI unbuildable, while
 * still passing every positive test in the suite.
 */
public final class FxUsingView {

    public Label label(String text) {
        return new Label(text);
    }
}
