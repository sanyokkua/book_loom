package ua.bookloom.ui.control;

import java.util.Arrays;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The wrapped label of one style class that the cards and screens repeat, and the one way a chip or a short status
 * word keeps every word whole when its row is narrow.
 */
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

    /**
     * Makes a label, typically a chip, give way by wrapping instead of ending in an ellipsis: it is never narrower than
     * its widest word and grows downwards for the rest, so a narrow row or a long language never shows half a word.
     *
     * <p>Nothing is logged here: the width is measured again whenever the text, the font or the padding changes.
     *
     * @param label the label; its text may change later
     * @param <T> the label's type
     * @return the same label
     */
    public static <T extends Labeled> T wholeWords(final T label) {
        Objects.requireNonNull(label, "label");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        final Text ruler = new Text();
        final InvalidationListener measure = observed -> label.setMinWidth(widestWord(label, ruler));
        label.textProperty().addListener(measure);
        label.fontProperty().addListener(measure);
        label.insetsProperty().addListener(measure);
        label.graphicProperty().addListener(measure);
        measure.invalidated(null);
        return label;
    }

    private static double widestWord(final Labeled label, final Text ruler) {
        ruler.setFont(label.getFont());
        final String text = label.getText() == null ? "" : label.getText();
        final double word = Arrays.stream(text.split("\\s+"))
                .mapToDouble(part -> {
                    ruler.setText(part);
                    return ruler.getLayoutBounds().getWidth();
                })
                .max()
                .orElse(0);
        final Insets insets = label.getInsets();
        final Node graphic = label.getGraphic();
        final double icon = graphic == null ? 0 : graphic.prefWidth(-1) + label.getGraphicTextGap();
        // A pixel more than the measured word: the skin rounds its text area down and would end the word in "…".
        return Math.ceil(word + insets.getLeft() + insets.getRight() + icon) + 1;
    }
}
