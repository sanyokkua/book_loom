package ua.bookloom.ui.control;

import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.dialog.SystemTextClipboard;
import ua.bookloom.ui.dialog.TextClipboard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.util.text.JsonLayout;

/**
 * The Detailed view's wire blocks of a call: the request body as it went out and the reply body as it came back, each
 * laid out for reading in a collapsed block with Copy. A block is hidden until its body exists, so a request shows
 * what was sent before the model answers. Showing logs nothing, as it is redrawn whenever a live call changes.
 */
final class WireView extends VBox {

    private static final double SPACING = 8;

    private final RawBlock request;
    private final RawBlock reply;
    private @Nullable String requestBody;
    private @Nullable String replyBody;

    WireView(final String id, final Messages messages) {
        this(id, messages, new SystemTextClipboard());
    }

    WireView(final String id, final Messages messages, final TextClipboard clipboard) {
        super(SPACING);
        setId(id);
        request = new RawBlock(
                id + "-request",
                messages,
                clipboard,
                () -> JsonLayout.pretty(requestBody == null ? "" : requestBody),
                () -> true,
                new RawBlock.Keys(
                        MessageKey.LIVE_CALL_PROMPT_TIP,
                        MessageKey.LIVE_CALL_PROMPT_COPY,
                        MessageKey.LIVE_CALL_PROMPT_COPY_TIP));
        reply = new RawBlock(
                id + "-reply",
                messages,
                clipboard,
                () -> JsonLayout.pretty(replyBody == null ? "" : replyBody),
                () -> true,
                new RawBlock.Keys(
                        MessageKey.LIVE_CALL_REPLY_TIP,
                        MessageKey.LIVE_CALL_REPLY_COPY,
                        MessageKey.LIVE_CALL_REPLY_COPY_TIP));
        getChildren().addAll(request, reply);
        Visibility.show(this, false);
    }

    /**
     * Shows the bodies of the selected call.
     *
     * @param sent the JSON the attempt put on the wire, or null when there is none to show
     * @param received the reply body as the provider sent it, or null before the call is answered
     * @param messages the catalogue the block titles are worded from
     */
    void show(final @Nullable String sent, final @Nullable String received, final Messages messages) {
        requestBody = sent;
        replyBody = received;
        request.display(messages.get(MessageKey.PROGRESS_WIRE_REQUEST), sent != null);
        reply.display(messages.get(MessageKey.PROGRESS_WIRE_REPLY), received != null);
        Visibility.show(this, sent != null || received != null);
    }
}
