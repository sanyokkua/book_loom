package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Gives a one-line label its whole text as a tooltip only while the text is cut by an ellipsis, so a tooltip is never
 * noise on a text that fits and a cut text is never left without its rest.
 *
 * <p>Nothing is logged here: the check runs whenever the label's width, text or font changes.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Overrun {

    private static final double TOLERANCE = 0.5;

    /**
     * Starts watching a label. A tooltip somebody else set is left alone.
     *
     * @param label a label that shows one line and ends it in an ellipsis; a wrapping label is never cut, so it never
     *     gets a tooltip
     */
    public static void install(final Labeled label) {
        install(label, null);
    }

    /**
     * Starts watching a label that shows a part of a longer text, which the tooltip then holds whole.
     *
     * @param label a label that shows one line and ends it in an ellipsis
     * @param whole the text the tooltip shows while the label is cut, or {@code null} for the label's own text
     */
    public static void install(final Labeled label, final @Nullable String whole) {
        Objects.requireNonNull(label, "label");
        final Watch watch = new Watch(label, whole);
        label.widthProperty().addListener((observed, was, now) -> watch.refresh());
        label.textProperty().addListener((observed, was, now) -> watch.refresh());
        label.fontProperty().addListener((observed, was, now) -> watch.refresh());
        watch.refresh();
    }

    private static final class Watch {
        private final Labeled label;
        private final Text ruler = new Text();
        private final @Nullable String whole;
        private @Nullable Tooltip own;

        Watch(final Labeled label, final @Nullable String whole) {
            this.label = label;
            this.whole = whole;
        }

        void refresh() {
            final String text = label.getText();
            if (isCut(text)) {
                show(whole == null ? text : whole);
            } else {
                hide();
            }
        }

        private boolean isCut(final @Nullable String text) {
            return text != null
                    && !text.isEmpty()
                    && !label.isWrapText()
                    && label.getWidth() > 0
                    && label.getWidth() + TOLERANCE < wantedWidth(text);
        }

        // Measured from the text, not asked of the label: a label whose preferred width was set answers that instead.
        private double wantedWidth(final String text) {
            ruler.setText(text);
            ruler.setFont(label.getFont());
            final Insets insets = label.getInsets();
            final Node graphic = label.getGraphic();
            final double icon = graphic == null ? 0 : graphic.prefWidth(-1) + label.getGraphicTextGap();
            final double measured = ruler.getLayoutBounds().getWidth() + insets.getLeft() + insets.getRight() + icon;
            // The skin asks a little more than the bare text on some platforms, and ends the line in an ellipsis below
            // that, so a label is cut from the width it asks for, when nobody set it.
            return label.getPrefWidth() == Region.USE_COMPUTED_SIZE
                    ? Math.max(measured, label.prefWidth(-1))
                    : measured;
        }

        private void show(final String text) {
            final Tooltip held = label.getTooltip();
            if (held != null && !held.equals(own)) {
                return;
            }
            if (held == null || !text.equals(held.getText())) {
                Tips.install(label, text);
                own = label.getTooltip();
            }
        }

        private void hide() {
            if (own != null && own.equals(label.getTooltip())) {
                label.setTooltip(null);
                label.setAccessibleHelp(null);
            }
            own = null;
        }
    }
}
