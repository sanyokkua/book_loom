package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportStages;
import ua.bookloom.ui.state.ProgressDialogModel;

/**
 * The export dialog's left side in two sections: the checks before the file is written, and the writing of the file.
 * Each section is the same step list over the rows it keeps, so the selection and the follow switch work as in every
 * other progress dialog.
 */
final class ExportStepSections extends VBox {

    private static final double SPACING = 8;
    private static final int WRITING = ExportStages.WRITING;

    ExportStepSections(final ProgressDialogModel model, final Messages messages) {
        Objects.requireNonNull(messages, "messages");
        super(SPACING);
        setId("export-step-sections");
        final ProgressStepList stages = new ProgressStepList(
                model, messages, row -> row.kind() == StepKind.STAGE && row.index() < WRITING, true);
        stages.sizeToRows(true);
        final ProgressStepList finish = new ProgressStepList(
                model, messages, row -> row.kind() == StepKind.STAGE && row.index() == WRITING, false);
        finish.sizeToRows(true);
        getChildren()
                .addAll(
                        heading(messages.get(MessageKey.EXPORT_GROUP_STEPS)),
                        stages,
                        heading(messages.get(MessageKey.EXPORT_GROUP_FINISH)),
                        finish);
    }

    private static Label heading(final String text) {
        final Label label = new Label(text);
        label.getStyleClass().add("progress-heading");
        return label;
    }
}
