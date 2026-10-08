package ua.bookloom.ui.control;

import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** A model call's reply as received, collapsed, with Copy; hidden until the call is answered. */
final class ReplyPane extends CopyablePane {

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
        show(null);
    }

    void show(final @Nullable String reply) {
        if (reply == null) {
            hideSection();
            return;
        }
        display(messages().get(MessageKey.LIVE_CALL_REPLY), reply, List.of(text("context-text", reply)));
    }
}
