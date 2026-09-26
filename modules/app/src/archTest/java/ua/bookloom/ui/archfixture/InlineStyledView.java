package ua.bookloom.ui.archfixture;

import javafx.scene.control.Label;

/**
 * Violation fixture for {@code no-inline-style-in-ui}: a {@code :ui} class that styles one element from code with
 * {@code Label#setStyle}, which no theme swap can reach.
 */
public final class InlineStyledView {

    public Label label(final String text) {
        final Label label = new Label(text);
        label.setStyle("-fx-text-fill: red;");
        return label;
    }
}
