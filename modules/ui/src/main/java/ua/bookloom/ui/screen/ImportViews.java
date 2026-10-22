package ua.bookloom.ui.screen;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The small parts the import page and the screens after it share: a key and its value on a ruled row, the wrapped
 * label and the format's name.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ImportViews {

    static final String WARNING_GLYPH = "⚠";

    static Label wrapped(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** One key and its wrapped value on a ruled row, whose whole node id is {@code id}. */
    static Node keyValue(final String id, final String label, final String value) {
        final Label key = new Label(label);
        key.getStyleClass().add("kv-key");
        final Label shown = new Label(value);
        shown.getStyleClass().add("kv-value");
        shown.setWrapText(true);
        final HBox row = new HBox(key, shown);
        row.setId(id);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("kv");
        return row;
    }

    /** As {@link #keyValue} but the value is a node, such as a chip. */
    static Node keyValueNode(final String id, final String label, final Node value) {
        final Label key = new Label(label);
        key.getStyleClass().add("kv-key");
        final HBox row = new HBox(key, value);
        row.setId(id);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("kv");
        return row;
    }

    static MessageKey formatName(final BookFormat format) {
        return switch (format) {
            case EPUB -> MessageKey.IMPORT_FORMAT_EPUB;
            case FB2 -> MessageKey.IMPORT_FORMAT_FB2;
            case MARKDOWN -> MessageKey.IMPORT_FORMAT_MARKDOWN;
            case TXT -> MessageKey.IMPORT_FORMAT_TXT;
        };
    }
}
