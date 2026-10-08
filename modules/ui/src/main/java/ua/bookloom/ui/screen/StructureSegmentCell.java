package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StructureSegmentRow;

/**
 * One row of the structure screen's segment list: locator, kind chip, the text on one ellipsised line, the kept chips,
 * the token estimate and the planned-chunk badge. Its nodes are made once per cell and only refilled as the cell is
 * reused, and it logs nothing, because it is refreshed on every scroll.
 *
 * <p>The row has the list's one fixed height, so the text never wraps; the whole text is in the reading pane.
 */
final class StructureSegmentCell extends ListCell<StructureSegmentRow> {

    private static final double GAP = 8;
    private static final double CELL_PADDING = 12;
    private static final double LOCATOR_WIDTH = 78;
    private static final double TOKENS_WIDTH = 62;
    private static final String CHUNK_START = "chunk-start";
    private static final String CHUNK_A = "chip-chunk-a";
    private static final String CHUNK_B = "chip-chunk-b";

    private final Messages messages;
    private final NumberFormat numbers;
    private final Label locator = new Label();
    private final Label kind = chip("structure-seg-kind", "chip-neutral");
    private final Label text = new Label();
    private final Label verbatim = chip("structure-seg-kept", "chip-neutral");
    private final Label source = chip("structure-seg-source", "chip-neutral");
    private final Label tokens = new Label();
    private final Label chunk = chip("structure-seg-chunk", CHUNK_A);
    private final HBox line = new HBox(GAP, locator, kind, text, verbatim, source, tokens, chunk);

    StructureSegmentCell(final Messages messages, final NumberFormat numbers) {
        this.messages = messages;
        this.numbers = numbers;
        locator.getStyleClass().addAll("review-locator", "structure-seg-locator");
        locator.setMinWidth(LOCATOR_WIDTH);
        locator.setPrefWidth(LOCATOR_WIDTH);
        text.getStyleClass().add("structure-seg-text");
        text.setTextOverrun(OverrunStyle.ELLIPSIS);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        tokens.getStyleClass().addAll("muted", "structure-seg-tokens");
        tokens.setMinWidth(TOKENS_WIDTH);
        tokens.setAlignment(Pos.CENTER_RIGHT);
        verbatim.setText(messages.get(MessageKey.STRUCTURE_KEPT_VERBATIM));
        source.setText(messages.get(MessageKey.STRUCTURE_KEPT_SOURCE));
        Tips.installOnHover(messages, verbatim, MessageKey.STRUCTURE_KEPT_VERBATIM_TIP);
        Tips.installOnHover(messages, source, MessageKey.STRUCTURE_KEPT_SOURCE_TIP);
        Tips.installOnHover(messages, chunk, MessageKey.STRUCTURE_CHUNK_HINT);
        Tips.installOnHover(messages, tokens, MessageKey.STRUCTURE_TOKENS_HINT);
        for (final Region chip : new Region[] {kind, verbatim, source, chunk}) {
            chip.setMinWidth(Region.USE_PREF_SIZE);
        }
        line.setAlignment(Pos.CENTER_LEFT);
        // A cell lays its graphic out at the graphic's own width, so the row is held to the cell's width here; with
        // no preferred width of its own the cell never makes the list scroll sideways for a long text.
        setPrefWidth(0);
        text.setPrefWidth(0);
        line.prefWidthProperty().bind(widthProperty().subtract(CELL_PADDING));
    }

    @Override
    protected void updateItem(final @Nullable StructureSegmentRow row, final boolean empty) {
        super.updateItem(row, empty);
        setText(null);
        getStyleClass().remove(CHUNK_START);
        if (empty || row == null) {
            setGraphic(null);
            return;
        }
        final SegmentPreview preview = row.preview();
        locator.setText(preview.locator());
        kind.setText(messages.get(StructureKindLabels.of(preview.kind())));
        text.setText(preview.displaySource());
        tokens.setText(numbers.format(preview.tokenEstimate()));
        shown(verbatim, preview.keptVerbatim());
        shown(source, preview.keptAsSource());
        showChunk(preview);
        if (row.chunkStart()) {
            getStyleClass().add(CHUNK_START);
        }
        setGraphic(line);
    }

    private void showChunk(final SegmentPreview preview) {
        final boolean chunked = preview.chunkIndex() > 0;
        shown(chunk, chunked);
        if (!chunked) {
            return;
        }
        chunk.setText(preview.chunkIndex() + "/" + preview.chunkCount());
        chunk.getStyleClass().removeAll(CHUNK_A, CHUNK_B);
        chunk.getStyleClass().add(preview.chunkIndex() % 2 == 1 ? CHUNK_A : CHUNK_B);
    }

    private static Label chip(final String id, final String role) {
        final Label label = new Label();
        label.getStyleClass().addAll("chip", id, role);
        return label;
    }

    private static void shown(final Label label, final boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }
}
