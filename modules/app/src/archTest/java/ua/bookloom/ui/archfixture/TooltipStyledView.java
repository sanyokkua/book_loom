package ua.bookloom.ui.archfixture;

import javafx.scene.control.Tooltip;

/**
 * Violation fixture for {@code no-inline-style-in-ui}: {@code Tooltip} is not a {@code Node}, yet its
 * {@code setStyle} bypasses the token sheet just the same, so the rule must reach owners beyond {@code Node}.
 */
public final class TooltipStyledView {

    public Tooltip tooltip(final String text) {
        final Tooltip tooltip = new Tooltip(text);
        tooltip.setStyle("-fx-background-color: red;");
        return tooltip;
    }
}
