package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The filled parts of one model call's prompt, collapsed, in the order the call sent them. A part is shown under the
 * heading the template gave it (its slot name when the template gave none), and a divider names the system and the
 * user message where the prompt switches from one to the other. The fixed instruction text is not here, except when
 * the person switches on the full prompt, which shows the messages exactly as the request carried them; a call that
 * has no parts of its own (a glossary scan, a setup proposal) shows the full prompt always.
 *
 * <p>The body is one read-only text, filled only while the section is open, and the same text is what Copy puts on the
 * clipboard.
 */
public final class PromptContextPane extends TextBodyPane {

    private static final String PLAIN_SEPARATOR = "\n\n";

    private final CheckBox full = new CheckBox();
    private List<PromptSection> parts = List.of();
    private List<ChatMessage> sent = List.of();

    /**
     * Builds a hidden, collapsed section; {@link #show(List)} fills it.
     *
     * @param id the section's node id; the body is {@code <id>-body}, Copy is {@code <id>-copy} and the full-prompt
     *     switch is {@code <id>-full}
     * @param messages the catalogue the headings are worded from
     */
    public PromptContextPane(final String id, final Messages messages) {
        this(id, messages, CopyablePane::toClipboard);
    }

    PromptContextPane(final String id, final Messages messages, final Consumer<String> clipboard) {
        super(
                id,
                messages,
                clipboard,
                MessageKey.LIVE_CALL_PROMPT_TIP,
                MessageKey.LIVE_CALL_PROMPT_COPY,
                MessageKey.LIVE_CALL_PROMPT_COPY_TIP);
        full.setId(id + "-full");
        full.setText(messages.get(MessageKey.LIVE_CALL_PROMPT_FULL));
        Tips.install(messages, full, MessageKey.LIVE_CALL_PROMPT_FULL_TIP);
        full.setOnAction(event -> render());
        final VBox content = (VBox) getContent();
        ((HBox) content.getChildren().getFirst()).getChildren().addFirst(full);
        render();
    }

    /**
     * Shows the parts of a prompt, or hides the section when there are none.
     *
     * @param sections the parts in the order they were sent, the system message's first
     */
    public void show(final List<PromptSection> sections) {
        show(sections, List.of());
    }

    /**
     * Shows the parts of a prompt and, behind the full-prompt switch, the messages as sent.
     *
     * @param sections the parts in the order they were sent, the system message's first
     * @param messages the request's messages in wire order, or empty when the call site did not say
     */
    public void show(final List<PromptSection> sections, final List<ChatMessage> messages) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(messages, "messages");
        if (sections.equals(parts) && messages.equals(sent)) {
            return;
        }
        parts = sections;
        sent = messages;
        render();
    }

    @Override
    void rename(final String id) {
        super.rename(id);
        full.setId(id + "-full");
    }

    @Override
    String plainText() {
        if (sent.isEmpty() && parts.isEmpty()) {
            return "";
        }
        return isShowingSent() ? plainSent() : plain(parts);
    }

    private boolean isShowingSent() {
        return !sent.isEmpty() && (full.isSelected() || parts.isEmpty());
    }

    private void render() {
        final boolean toggleable = !sent.isEmpty() && !parts.isEmpty();
        full.setVisible(toggleable);
        full.setManaged(toggleable);
        if (parts.isEmpty() && sent.isEmpty()) {
            hideSection();
        } else if (isShowingSent()) {
            showSection(messages().get(MessageKey.LIVE_CALL_PROMPT_FULL_TITLE, sent.size()));
        } else {
            showSection(messages().get(MessageKey.LIVE_CALL_PROMPT, parts.size()));
        }
        refill();
    }

    private String plainSent() {
        final StringJoiner text = new StringJoiner(PLAIN_SEPARATOR);
        for (final ChatMessage message : sent) {
            text.add(divider(roleKey(message.role())));
            text.add(message.content());
        }
        return text.toString();
    }

    private String plain(final List<PromptSection> sections) {
        final StringJoiner text = new StringJoiner(PLAIN_SEPARATOR);
        PromptSection.@Nullable Origin shown = null;
        for (final PromptSection section : sections) {
            if (section.origin() != shown) {
                shown = section.origin();
                text.add(divider(originKey(shown)));
            }
            text.add(headingOf(section) + "\n" + String.join("\n", section.lines()));
        }
        return text.toString();
    }

    private String divider(final MessageKey key) {
        return "== " + messages().get(key) + " ==";
    }

    private static MessageKey roleKey(final ChatRole role) {
        return switch (role) {
            case SYSTEM -> MessageKey.LIVE_CALL_PROMPT_SYSTEM;
            case USER -> MessageKey.LIVE_CALL_PROMPT_USER;
            case ASSISTANT -> MessageKey.LIVE_CALL_PROMPT_ASSISTANT;
        };
    }

    private static String headingOf(final PromptSection section) {
        return section.heading().isBlank() ? section.slot() : section.heading();
    }

    private static MessageKey originKey(final PromptSection.Origin origin) {
        return switch (origin) {
            case SYSTEM -> MessageKey.LIVE_CALL_PROMPT_SYSTEM;
            case USER -> MessageKey.LIVE_CALL_PROMPT_USER;
        };
    }
}
