package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Text exactly as it was sent or received, in a collapsed block of its own with Copy, for when the parsed view hides
 * something a person needs to see. The text is built when the block and the view holding it are open or when it is
 * copied, never while either is closed.
 */
final class RawBlock extends TextBodyPane {

    private final Supplier<String> text;
    private final BooleanSupplier hostOpen;

    RawBlock(
            final String id,
            final Messages messages,
            final Consumer<String> clipboard,
            final Supplier<String> text,
            final BooleanSupplier hostOpen,
            final MessageKey tip,
            final MessageKey copyLabel,
            final MessageKey copyTip) {
        super(id, messages, clipboard, tip, copyLabel, copyTip);
        this.text = Objects.requireNonNull(text, "text");
        this.hostOpen = Objects.requireNonNull(hostOpen, "hostOpen");
        hideSection();
    }

    /** Shows the block under a title, or hides it when there is nothing to show; the text is read again if open. */
    void display(final String title, final boolean visible) {
        if (visible) {
            showSection(title);
        } else {
            hideSection();
        }
        refill();
    }

    @Override
    boolean isOpen() {
        return super.isOpen() && hostOpen.getAsBoolean();
    }

    @Override
    String plainText() {
        return text.get();
    }
}
