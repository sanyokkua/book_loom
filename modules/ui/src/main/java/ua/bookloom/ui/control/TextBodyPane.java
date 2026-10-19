package ua.bookloom.ui.control;

import java.util.function.Consumer;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * A copyable section whose body is one read-only text area holding the same text Copy puts on the clipboard. The area
 * is filled only while the section is open: a live call changes many times while its sections stay closed, and a
 * closed section's text is never measured or laid out. Opening it fills the area; closing it empties it again.
 */
abstract class TextBodyPane extends CopyablePane {

    private final TextBody area;

    TextBodyPane(
            final String id,
            final Messages messages,
            final Consumer<String> clipboard,
            final MessageKey tip,
            final MessageKey copyLabel,
            final MessageKey copyTip) {
        this(id, messages, clipboard, tip, copyLabel, copyTip, new TextBody());
    }

    private TextBodyPane(
            final String id,
            final Messages messages,
            final Consumer<String> clipboard,
            final MessageKey tip,
            final MessageKey copyLabel,
            final MessageKey copyTip,
            final TextBody area) {
        super(id, messages, clipboard, tip, copyLabel, copyTip, area);
        this.area = area;
        expandedProperty().addListener((observed, was, now) -> refill());
    }

    /** Puts the section's current text into the area if it is open; called after what the section shows changed. */
    final void refill() {
        final String text = isExpanded() && isVisible() ? plainText() : "";
        if (!text.equals(area.getText())) {
            area.setText(text);
        }
    }
}
