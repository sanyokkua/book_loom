package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import ua.bookloom.api.pipeline.ProofreadItem;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The detail of a proofread window's live row: for each segment the window read, its locator with the verdict in
 * words, then the text before beside the text after. Rebuilt only when the selected row changes; logs nothing, as it
 * runs on every publication.
 */
final class WindowDetail extends VBox {

    private static final double SPACING = 10;
    private static final double[] HALVES = {1, 1};

    private final Messages messages;

    WindowDetail(final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        setId("translating-live-detail-window");
    }

    void show(final List<ProofreadItem> items) {
        getChildren().setAll(items.stream().map(this::item).toList());
    }

    private VBox item(final ProofreadItem item) {
        final Label head = Wrapped.wholeLines(new Label(messages.get(
                MessageKey.LIVE_DETAIL_WINDOW_ITEM,
                item.locator(),
                ProofreadWords.verdict(messages, item.outcome(), item.reason(), item.marks()))));
        head.getStyleClass().add("live-detail-locator");
        final GridPane texts = ReadOnlyGrid.of("translating-live-detail-window-texts", HALVES);
        final Label beforeHead = caption(MessageKey.LIVE_DETAIL_BEFORE);
        final Label afterHead = caption(MessageKey.LIVE_DETAIL_AFTER);
        texts.addRow(0, beforeHead, afterHead);
        texts.addRow(1, text(item.before()), text(item.after()));
        texts.setHgap(SPACING);
        texts.setVgap(SPACING / 2);
        final VBox box = new VBox(SPACING / 2, head, texts);
        box.getStyleClass().add("live-detail-window-item");
        return box;
    }

    private Label caption(final MessageKey key) {
        final Label label = new Label(messages.get(key));
        label.getStyleClass().add("stat-caption");
        return label;
    }

    private static Label text(final String text) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("live-detail-text");
        return label;
    }
}
