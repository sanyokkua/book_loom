package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Wrapped;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportViewModel;

/**
 * The right-hand column: the three side files written beside the book.
 *
 * <p>The card stays visible while a run translates and is unavailable then, because nothing can be exported until the
 * run pauses; the view model's run note is what says so, so the card follows it and never recomputes the run state.
 */
final class ExportSideFilesColumn {

    private static final double COLUMN_SPACING = 14;
    private static final double WIDTH = 320;

    private final ExportViewModel viewModel;
    private final Messages messages;

    ExportSideFilesColumn(final ExportViewModel viewModel, final Messages messages) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    Node build() {
        final VBox column = new VBox(COLUMN_SPACING, sideFilesCard());
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
        // The column is narrow; a long label (the bilingual copy's) wraps instead of ending in an ellipsis.
        Wrapped.wholeLines(box);
        box.setSelected(viewModel.sideFiles().contains(file));
        box.selectedProperty().addListener((observed, was, now) -> viewModel.setSideFile(file, now));
        box.disableProperty().bind(viewModel.runNote().isNotEmpty());
        return box;
    }
}
