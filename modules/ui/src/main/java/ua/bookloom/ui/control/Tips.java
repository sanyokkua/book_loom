package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Gives a control the hover explanation every operable element of the interface carries, so all of them look and time
 * the same: a short delay so a passing pointer shows nothing, a wrapped bubble of bounded width, and a duration long
 * enough to read it.
 *
 * <p>The same text is set as the control's accessible help, so what a pointer user reads on hover a screen-reader user
 * is told. A tooltip is never shown over a disabled control, because JavaFX delivers it no pointer events.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Tips {

    private static final Duration SHOW_DELAY = Duration.millis(400);
    private static final Duration SHOW_DURATION = Duration.seconds(30);
    private static final double MAX_WIDTH = 320;

    /**
     * Attaches the explanation {@code tip} names to {@code node}.
     *
     * @param messages the catalogue the text is drawn from
     * @param node the control or container the pointer hovers; a second call replaces the first tip
     * @param tip the catalogue entry holding the explanation
     * @param <N> the node's type, returned unchanged so a call can wrap a construction
     * @return {@code node}
     */
    public static <N extends Node> N install(final Messages messages, final N node, final MessageKey tip) {
        Objects.requireNonNull(messages, "messages");
        return install(node, messages.get(tip));
    }

    /**
     * Attaches an already translated explanation, for a caller that holds text rather than a key.
     *
     * @param node the control or container the pointer hovers; a second call replaces the first tip
     * @param text the non-blank explanation
     * @param <N> the node's type, returned unchanged
     * @return {@code node}
     */
    public static <N extends Node> N install(final N node, final String text) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(text, "text");
        final Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(SHOW_DELAY);
        tooltip.setShowDuration(SHOW_DURATION);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(MAX_WIDTH);
        if (node instanceof Control control) {
            control.setTooltip(tooltip);
            control.setAccessibleHelp(text);
        } else {
            Tooltip.install(node, tooltip);
        }
        return node;
    }

    /**
     * Gives a table column's header an explanation. A column header is not a node, so the caption moves into a label
     * that carries the tooltip.
     *
     * @param messages the catalogue the text is drawn from
     * @param column the column whose header is explained; its text becomes the label's
     * @param tip the catalogue entry holding the explanation
     * @param <S> the row type
     * @param <T> the cell type
     * @return {@code column}
     */
    public static <S, T> TableColumn<S, T> installOnHeader(
            final Messages messages, final TableColumn<S, T> column, final MessageKey tip) {
        Objects.requireNonNull(column, "column");
        final Label caption = new Label(column.getText());
        install(messages, caption, tip);
        column.setText(null);
        column.setGraphic(caption);
        return column;
    }
}
