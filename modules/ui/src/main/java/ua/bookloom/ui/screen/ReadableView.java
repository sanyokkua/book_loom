package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.control.PlaceholderFlow;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.EvidenceQuote;
import ua.bookloom.ui.state.ReadableText;

/**
 * The review panel's readable view of a segment: its source and its target as text in which each formatting token is a
 * chip and the words a finding quotes are marked. It is shown only when there is something to draw differently from the
 * two panes above it. No line is logged on a refresh, because the target follows every keystroke.
 */
final class ReadableView extends VBox {

    private static final double SPACING = 5;

    private final PlaceholderFlow source = new PlaceholderFlow("review-source-preview");
    private final PlaceholderFlow target = new PlaceholderFlow("review-target-preview");

    ReadableView(final Messages messages) {
        super(SPACING);
        final Label caption =
                new Label(Objects.requireNonNull(messages, "messages").get(MessageKey.REVIEW_READABLE));
        caption.getStyleClass().add("stat-caption");
        setId("review-readable");
        managedProperty().bind(visibleProperty());
        getChildren().addAll(caption, source, target);
    }

    /**
     * Draws a segment with the target text as it stands in the editor.
     *
     * @param view the selected segment, or {@code null} for none
     * @param editorText the target in masked form, with the person's typing in it
     */
    void show(final SegmentView view, final String editorText) {
        final List<String> quotes = EvidenceQuote.allOf(view.findings());
        setVisible(
                ReadableText.needsView(view.maskedSource(), List.of()) || ReadableText.needsView(editorText, quotes));
        source.show(view.maskedSource(), List.of());
        target.show(editorText, quotes);
    }
}
