package ua.bookloom.ui.control;

import javafx.scene.control.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The wrapped label of one style class that the cards and screens repeat. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Wrapped {

    /**
     * A label whose text wraps.
     *
     * @param text the words, possibly empty when they are set later
     * @param styleClass the one style class the label carries
     * @return a new label
     */
    public static Label label(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
