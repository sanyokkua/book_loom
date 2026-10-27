package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StepEntry;

/**
 * One reread item of the export's last check, read in its place: the paragraph before, this one and the paragraph
 * after, each with its source above its translation, in a Before and an After column. A fix shows the old text struck
 * through above the new one; an item left alone says so in the After column, and one still with the model says that.
 * Nothing here logs: it is redrawn whenever the selected step changes.
 */
final class RereadGrid extends GridPane {

    private static final int LABEL_COLUMN = 0;
    private static final int BEFORE_COLUMN = 1;
    private static final int AFTER_COLUMN = 2;
    private static final int SPAN = 2;
    private static final double GAP = 8;
    private static final String FIXED = "fixed";

    private final Messages messages;

    RereadGrid(final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        setId("reread-grid");
        getStyleClass().add("reread-grid");
        setHgap(GAP);
        setVgap(GAP);
        final ColumnConstraints label = new ColumnConstraints();
        final ColumnConstraints before = new ColumnConstraints();
        before.setHgrow(Priority.ALWAYS);
        final ColumnConstraints after = new ColumnConstraints();
        after.setHgrow(Priority.ALWAYS);
        getColumnConstraints().setAll(label, before, after);
    }

    /**
     * Shows a step's item, or nothing when the step reads no neighbours.
     *
     * @param text what the step read and wrote, or {@code null}
     * @param running whether the step's call is still under way
     * @return {@code true} if an item is shown, {@code false} otherwise
     */
    boolean show(final StepEntry.@Nullable StepText text, final boolean running) {
        getChildren().clear();
        final ExportProgress.Neighbours around = text == null ? null : text.neighbours();
        if (text == null || around == null) {
            return false;
        }
        add(cell(messages.get(MessageKey.EXPORT_REREAD_BEFORE), "reread-heading"), BEFORE_COLUMN, 0);
        add(cell(messages.get(MessageKey.EXPORT_REREAD_AFTER), "reread-heading"), AFTER_COLUMN, 0);
        add(cell(messages.get(MessageKey.EXPORT_REREAD_PREVIOUS), "reread-label"), LABEL_COLUMN, 1);
        add(paragraph(around.previousSource(), around.previousTarget()), BEFORE_COLUMN, 1, SPAN, 1);
        add(cell(messages.get(MessageKey.EXPORT_REREAD_THIS), "reread-label"), LABEL_COLUMN, 2);
        add(before(around.source(), text), BEFORE_COLUMN, 2);
        add(after(text, running), AFTER_COLUMN, 2);
        add(cell(messages.get(MessageKey.EXPORT_REREAD_NEXT), "reread-label"), LABEL_COLUMN, 3);
        add(paragraph(around.nextSource(), around.nextTarget()), BEFORE_COLUMN, 3, SPAN, 1);
        return true;
    }

    private Node paragraph(final @Nullable String source, final @Nullable String target) {
        return new VBox(cell(source == null ? "—" : source, "muted"), cell(target == null ? "" : target, null));
    }

    private Node before(final String source, final StepEntry.StepText text) {
        final String old = text.before() == null ? "" : text.before();
        final Node shown;
        if (FIXED.equals(text.verdict())) {
            final Text struck = new Text(old);
            struck.getStyleClass().add("reread-old");
            shown = new TextFlow(struck);
        } else {
            shown = cell(old, null);
        }
        return new VBox(cell(source, "muted"), shown);
    }

    private Node after(final StepEntry.StepText text, final boolean running) {
        if (running) {
            return cell(messages.get(MessageKey.EXPORT_REREAD_READING), "muted");
        }
        if (FIXED.equals(text.verdict()) && text.after() != null) {
            return cell(text.after(), null);
        }
        return cell(messages.get(MessageKey.EXPORT_REREAD_UNCHANGED), "muted");
    }

    private static Label cell(final String text, final @Nullable String styleClass) {
        final Label label = new Label(text);
        label.setWrapText(true);
        if (styleClass != null) {
            label.getStyleClass().add(styleClass);
        }
        return label;
    }
}
