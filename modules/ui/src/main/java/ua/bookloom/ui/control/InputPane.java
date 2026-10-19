package ua.bookloom.ui.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * What a call about no segment sent: the user message as the request carried it, with Copy. It is the only place such
 * a call's own text (the names to scan, the terms to judge) can be read, so unlike the prompt section it opens when it
 * first appears; after that it stays as the person leaves it while the call changes.
 */
final class InputPane extends TextBodyPane {

    private static final String PARAGRAPH = "\n\n";

    private String input = "";

    InputPane(final String id, final Messages messages) {
        this(id, messages, CopyablePane::toClipboard);
    }

    InputPane(final String id, final Messages messages, final Consumer<String> clipboard) {
        super(
                id,
                messages,
                clipboard,
                MessageKey.LIVE_CALL_INPUT_TIP,
                MessageKey.LIVE_CALL_INPUT_COPY,
                MessageKey.LIVE_CALL_INPUT_COPY_TIP);
        hideSection();
    }

    /**
     * Shows the user message of a request, or hides the section when there is none to show.
     *
     * @param sent the request's messages in wire order, or {@code null} when this call's input is not shown here
     */
    void show(final @Nullable List<ChatMessage> sent) {
        final String text = sent == null ? "" : userTextOf(sent);
        if (text.equals(input) && isVisible() == !text.isEmpty()) {
            return;
        }
        final boolean appears = !text.isEmpty() && !isVisible();
        input = text;
        if (text.isEmpty()) {
            hideSection();
        } else {
            showSection(messages().get(MessageKey.LIVE_CALL_INPUT));
            if (appears) {
                setExpanded(true);
            }
        }
        refill();
    }

    @Override
    String plainText() {
        return input;
    }

    private static String userTextOf(final List<ChatMessage> sent) {
        return sent.stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .collect(Collectors.joining(PARAGRAPH));
    }
}
