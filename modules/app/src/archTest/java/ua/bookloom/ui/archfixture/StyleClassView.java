package ua.bookloom.ui.archfixture;

import javafx.scene.control.Label;

/**
 * NEGATIVE control for {@code no-inline-style-in-ui}: a {@code :ui} class that styles through a style class, which
 * is the sanctioned way and which the rule must NOT flag. A rule that banned every styling call would reject this
 * while still rejecting {@link InlineStyledView}.
 */
public final class StyleClassView {

    public Label label(final String text) {
        final Label label = new Label(text);
        label.getStyleClass().add("placeholder-label");
        return label;
    }
}
