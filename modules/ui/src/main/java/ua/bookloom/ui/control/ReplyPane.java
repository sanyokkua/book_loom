package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** A model call's reply as received, collapsed, with Copy; hidden until the call is answered. */
final class ReplyPane extends TextBodyPane {

    private @Nullable String reply;

    ReplyPane(final String id, final Messages messages) {
        this(id, messages, CopyablePane::toClipboard);
    }

    ReplyPane(final String id, final Messages messages, final Consumer<String> clipboard) {
        super(
                id,
                messages,
                clipboard,
                MessageKey.LIVE_CALL_REPLY_TIP,
                MessageKey.LIVE_CALL_REPLY_COPY,
                MessageKey.LIVE_CALL_REPLY_COPY_TIP);
        hideSection();
    }

    void show(final @Nullable String received) {
        if (Objects.equals(received, reply) && isVisible() == (received != null)) {
            return;
        }
        reply = received;
        if (received == null) {
            hideSection();
        } else {
            showSection(messages().get(MessageKey.LIVE_CALL_REPLY));
        }
        refill();
    }

    @Override
    String plainText() {
        final String received = reply;
        return received == null ? "" : received;
    }
}
