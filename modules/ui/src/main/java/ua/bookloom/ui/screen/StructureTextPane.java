package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.ui.control.PlaceholderFlow;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StructureSegmentRow;

/** The reading pane of the text browser: the picked segment in full, with a line saying what and where it is. */
final class StructureTextPane extends VBox {

    private static final double SPACING = 8;
    private static final double PREVIEW_HEIGHT = 120;
    private static final double PREVIEW_MIN_HEIGHT = 80;

    private final Messages messages;
    private final Label meta = new Label();
    private final PlaceholderFlow text = new PlaceholderFlow("structure-preview-text");

    StructureTextPane(final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        meta.setId("structure-preview-meta");
        meta.getStyleClass().add("muted");
        meta.setWrapText(true);
        text.getStyleClass().add("structure-preview-flow");
        final ScrollPane scroll = new ScrollPane(text);
        scroll.setId("structure-preview");
        scroll.getStyleClass().add("structure-preview");
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(PREVIEW_HEIGHT);
        scroll.setMinHeight(PREVIEW_MIN_HEIGHT);
        Tips.install(messages, scroll, MessageKey.STRUCTURE_PREVIEW_HINT);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().addAll(meta, scroll);
        show(null);
    }

    /** Shows a segment in full, or the invitation to pick one when {@code row} is {@code null}. */
    void show(final @Nullable StructureSegmentRow row) {
        if (row == null) {
            meta.setText(messages.get(MessageKey.STRUCTURE_PREVIEW_EMPTY));
            text.show("", List.of());
            return;
        }
        final SegmentPreview preview = row.preview();
        final String chunk = preview.chunkIndex() > 0
                ? messages.get(MessageKey.STRUCTURE_PREVIEW_CHUNK, preview.chunkIndex(), preview.chunkCount())
                : messages.get(MessageKey.STRUCTURE_PREVIEW_UNCHUNKED);
        final String caption = messages.get(
                MessageKey.STRUCTURE_PREVIEW_META,
                preview.locator(),
                messages.get(StructureKindLabels.of(preview.kind())),
                preview.tokenEstimate(),
                chunk);
        meta.setText(
                preview.oversized() ? caption + " · " + messages.get(MessageKey.STRUCTURE_PREVIEW_OVERSIZED) : caption);
        text.show(preview.maskedSource(), List.of());
    }
}
