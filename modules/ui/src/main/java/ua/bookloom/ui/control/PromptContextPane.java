package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The filled parts of one model call's prompt, collapsed, in the order the call sent them. A part is shown under the
 * heading the template gave it (its slot name when the template gave none), and a divider names the system and the
 * user message where the prompt switches from one to the other. The fixed instruction text is not here.
 */
public final class PromptContextPane extends CopyablePane {

    private static final String PLAIN_SEPARATOR = "\n\n";

    /**
     * Builds a hidden, collapsed section; {@link #show(List)} fills it.
     *
     * @param id the section's node id; the body is {@code <id>-body}, Copy is {@code <id>-copy} and each part is
     *     {@code <id>-part-<n>} with its heading {@code -head} and its text {@code -text}, counted from one
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
        show(List.of());
    }

    /**
     * Shows the parts of a prompt, or hides the section when there are none.
     *
     * @param sections the parts in the order they were sent, the system message's first
     */
    public void show(final List<PromptSection> sections) {
        Objects.requireNonNull(sections, "sections");
        if (sections.isEmpty()) {
            hideSection();
            return;
        }
        display(messages().get(MessageKey.LIVE_CALL_PROMPT, sections.size()), plain(sections), nodes(sections));
    }

    private List<Node> nodes(final List<PromptSection> sections) {
        final List<Node> nodes = new ArrayList<>();
        PromptSection.@Nullable Origin shown = null;
        int number = 0;
        for (final PromptSection section : sections) {
            if (section.origin() != shown) {
                shown = section.origin();
                nodes.add(divider(shown));
            }
            number++;
            nodes.add(part(getId() + "-part-" + number, section));
        }
        return nodes;
    }

    private Node divider(final PromptSection.Origin origin) {
        final Label label = new Label(messages().get(originKey(origin)));
        label.getStyleClass().addAll("context-heading", "context-origin");
        return label;
    }

    private static Node part(final String id, final PromptSection section) {
        final VBox part = block(headingOf(section), List.of(text("context-text", String.join("\n", section.lines()))));
        part.setId(id);
        part.getChildren().get(0).setId(id + "-head");
        part.getChildren().get(1).setId(id + "-text");
        return part;
    }

    private String plain(final List<PromptSection> sections) {
        final StringJoiner text = new StringJoiner(PLAIN_SEPARATOR);
        PromptSection.@Nullable Origin shown = null;
        for (final PromptSection section : sections) {
            if (section.origin() != shown) {
                shown = section.origin();
                text.add("== " + messages().get(originKey(shown)) + " ==");
            }
            text.add(headingOf(section) + "\n" + String.join("\n", section.lines()));
        }
        return text.toString();
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
