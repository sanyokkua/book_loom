package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportViewModel;

/**
 * The right-hand cards: the three side files written beside the book and the final consistency pass.
 *
 * <p>Both stay visible while a run translates and are unavailable then, because nothing can be exported until the run
 * pauses; the view model's run note is what says so, so the cards follow it and never recompute the run state.
 */
@Slf4j
final class ExportSideFilesColumn {

    private static final double COLUMN_SPACING = 14;
    private static final double WIDTH = 320;

    private final ExportViewModel viewModel;
    private final Messages messages;
    private final ToggleSwitch consistency = new ToggleSwitch();
    private final ChangeListener<Boolean> onConsistency = (observed, was, now) -> consistency.setSelected(now);

    ExportSideFilesColumn(final ExportViewModel viewModel, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    Node build() {
        final VBox also = sideFilesCard();
        consistency.setId("export-aux-consistency");
        consistency.setAccessibleText(messages.get(MessageKey.EXPORT_CONSISTENCY_TITLE));
        consistency.setSelected(viewModel.consistencyPass().get());
        consistency.selectedProperty().addListener((observed, was, now) -> viewModel.setConsistencyPass(now));
        viewModel.consistencyPass().addListener(new WeakChangeListener<>(onConsistency));
        consistency.disableProperty().bind(viewModel.runNote().isNotEmpty());
        Tips.install(consistency, messages.get(MessageKey.EXPORT_CONSISTENCY_TITLE_TIP));
        final VBox pass = BriefCards.card(
                "export-consistency-card",
                messages,
                MessageKey.EXPORT_CONSISTENCY_TITLE,
                consistency,
                BriefCards.hint(messages, MessageKey.EXPORT_CONSISTENCY_NOTE));
        final VBox column = new VBox(COLUMN_SPACING, also, pass);
        column.setPrefWidth(WIDTH);
        column.setMinWidth(WIDTH);
        column.setMaxWidth(WIDTH);
        return column;
    }

    private VBox sideFilesCard() {
        return BriefCards.card(
                "export-also-card",
                messages,
                MessageKey.EXPORT_ALSO_TITLE,
                sideFile(
                        "export-aux-glossary",
                        MessageKey.EXPORT_AUX_GLOSSARY,
                        MessageKey.EXPORT_AUX_GLOSSARY_TIP,
                        SideFile.GLOSSARY_CSV),
                sideFile(
                        "export-aux-bilingual",
                        MessageKey.EXPORT_AUX_BILINGUAL,
                        MessageKey.EXPORT_AUX_BILINGUAL_TIP,
                        SideFile.BILINGUAL_HTML),
                sideFile(
                        "export-aux-report",
                        MessageKey.EXPORT_AUX_REPORT,
                        MessageKey.EXPORT_AUX_REPORT_TIP,
                        SideFile.QUALITY_REPORT));
    }

    private CheckBox sideFile(final String id, final MessageKey label, final MessageKey tip, final SideFile file) {
        final CheckBox box = Tips.install(messages, new CheckBox(messages.get(label)), tip);
        box.setId(id);
        box.setSelected(viewModel.sideFiles().contains(file));
        box.selectedProperty().addListener((observed, was, now) -> viewModel.setSideFile(file, now));
        box.disableProperty().bind(viewModel.runNote().isNotEmpty());
        return box;
    }
}
