package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The review panel's two panes side by side: the source, which cannot be changed, and the target, which can.
 *
 * <p>Both show the masked text the desk returns, with the book's {@code ⟦gN⟧} tokens as typed characters, so an edit
 * keeps the bold and the links where they were. No masking or restoring happens here; the caller sets the texts and
 * reads the target back as the person left it.
 */
public final class ComparePanes extends HBox {

    private static final double SPACING = 12;
    private static final double PANE_SPACING = 6;
    private static final int ROWS = 5;

    private final TextArea source = area("review-source", false);
    private final TextArea target = area("review-target", true);

    /**
     * Builds the two panes.
     *
     * @param sourceName the heading of the source pane, already translated
     * @param targetName the heading of the target pane, already translated
     * @param messages the catalogue the mark on the target pane is worded from
     */
    public ComparePanes(
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final Messages messages) {
        super(SPACING);
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(messages, "messages");
        final Label mark = new Label(messages.get(MessageKey.REVIEW_EDITABLE));
        mark.setId("review-editable-mark");
        mark.getStyleClass().addAll("chip", "chip-ok");
        getChildren()
                .addAll(
                        pane("review-source-head", sourceName, null, source),
                        pane("review-target-head", targetName, mark, target));
    }

    /**
     * The source pane.
     *
     * @return the read-only text area holding the segment's masked source
     */
    public TextArea source() {
        return source;
    }

    /**
     * The target pane.
     *
     * @return the editable text area holding the masked target
     */
    public TextArea target() {
        return target;
    }

    private static TextArea area(final String id, final boolean editable) {
        final TextArea area = new TextArea();
        area.setId(id);
        area.setEditable(editable);
        area.setWrapText(true);
        area.setPrefRowCount(ROWS);
        area.setMinWidth(0);
        area.getStyleClass().add("brief-input");
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    private static VBox pane(
            final String headId,
            final ObservableValue<String> heading,
            final @Nullable Label mark,
            final TextArea body) {
        final Label head = new Label();
        head.setId(headId);
        head.getStyleClass().add("stat-caption");
        head.textProperty().bind(heading);
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox header = mark == null ? new HBox(PANE_SPACING, head) : new HBox(PANE_SPACING, head, spacer, mark);
        final VBox pane = new VBox(PANE_SPACING, header, body);
        pane.setPrefWidth(0);
        pane.setMinWidth(0);
        HBox.setHgrow(pane, Priority.ALWAYS);
        return pane;
    }
}
