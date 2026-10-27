package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportStages;
import ua.bookloom.ui.state.ProgressDialogModel;
import ua.bookloom.ui.state.StepEntry;

/**
 * The export dialog's left side in three sections: the stages before the reread, the reread items under a heading that
 * counts them ("Reread next to the neighbours · 6 of 18"), and the writing of the file. Each section is the same step
 * list over the rows it keeps, so the selection and the follow switch work as in every other progress dialog. Nothing
 * here logs: the rows change up to ten times a second.
 */
final class ExportStepSections extends VBox {

    private static final double SPACING = 8;
    private static final int REREAD = ExportStages.REREAD;
    private static final int WRITING = ExportStages.WRITING;

    private final ProgressDialogModel model;
    private final Label reread = new Label();

    ExportStepSections(final ProgressDialogModel model, final Messages messages) {
        super(SPACING);
        this.model = Objects.requireNonNull(model, "model");
        Objects.requireNonNull(messages, "messages");
        setId("export-step-sections");
        final ProgressStepList stages = new ProgressStepList(
                model, messages, row -> row.kind() == StepKind.STAGE && row.index() < REREAD, true);
        stages.sizeToRows(true);
        final ProgressStepList items = new ProgressStepList(
                model, messages, row -> row.kind() == StepKind.REQUEST && row.itemRef() != null, false);
        // A step list answers its own preferred height as its maximum, so it must be told it may take what is left.
        items.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(items, Priority.ALWAYS);
        final ProgressStepList finish = new ProgressStepList(
                model, messages, row -> row.kind() == StepKind.STAGE && row.index() == WRITING, false);
        finish.sizeToRows(true);
        reread.setId("export-group-reread");
        reread.getStyleClass().add("progress-heading");
        getChildren()
                .addAll(
                        heading(messages.get(MessageKey.EXPORT_GROUP_STEPS)),
                        stages,
                        reread,
                        items,
                        heading(messages.get(MessageKey.EXPORT_GROUP_FINISH)),
                        finish);
        model.rows().addListener((ListChangeListener<StepEntry>) change -> showReread());
        showReread();
    }

    // The reread stage's own row carries the count, so the heading reads it from there once the stage has started.
    private void showReread() {
        final String label = model.rows().stream()
                .filter(row -> row.kind() == StepKind.STAGE && row.index() == REREAD)
                .map(StepEntry::label)
                .findFirst()
                .orElse("");
        reread.setText(label);
        reread.setVisible(!label.isEmpty());
        reread.setManaged(!label.isEmpty());
    }

    private static Label heading(final String text) {
        final Label label = new Label(text);
        label.getStyleClass().add("progress-heading");
        return label;
    }
}
