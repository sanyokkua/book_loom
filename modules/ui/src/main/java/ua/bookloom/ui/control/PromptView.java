package ua.bookloom.ui.control;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The prompt of one model call as a person reads it: a card per message (System, User) holding a section per filled
 * part, each in the shape its slot has ({@link PromptParts}), and behind them the messages exactly as the request
 * carried them in a collapsed raw block with its own Copy. The fixed instruction text appears only there; a call with
 * no parts of its own (a glossary scan, a setup proposal) opens the raw block at once.
 *
 * <p>Copy on the view puts the parts on the clipboard as text, in the order they were sent under their message. Showing
 * logs nothing, as it is redrawn whenever a live call changes; only a press of Copy is logged.
 */
public final class PromptView extends CopyablePane {

    private static final String PLAIN_SEPARATOR = "\n\n";
    private static final double SPACING = 8;

    private final VBox cards;
    private final RawBlock sentBlock;
    private List<PromptSection> parts = List.of();
    private List<ChatMessage> sent = List.of();
    private Map<String, String> locators = Map.of();
    private boolean wasPartless;

    /**
     * Builds a hidden, collapsed view; {@link #show} fills it.
     *
     * @param id the view's node id; Copy is {@code <id>-copy}, the raw messages are {@code <id>-sent} and a part is
     *     {@code <id>-<slot>}
     * @param messages the catalogue the headings are worded from
     */
    public PromptView(final String id, final Messages messages) {
        this(id, messages, CopyablePane::toClipboard);
    }

    PromptView(final String id, final Messages messages, final Consumer<String> clipboard) {
        this(id, messages, clipboard, new VBox(SPACING));
    }

    private PromptView(final String id, final Messages messages, final Consumer<String> clipboard, final VBox cards) {
        super(
                id,
                messages,
                clipboard,
                MessageKey.LIVE_CALL_PROMPT_TIP,
                MessageKey.LIVE_CALL_PROMPT_COPY,
                MessageKey.LIVE_CALL_PROMPT_COPY_TIP,
                new ScrollingBody(cards));
        this.cards = cards;
        cards.getStyleClass().add("context-sections");
        this.sentBlock = new RawBlock(
                id + "-sent",
                messages,
                clipboard,
                this::plainSent,
                this::isExpanded,
                MessageKey.LIVE_CALL_PROMPT_FULL_TITLE_TIP,
                MessageKey.LIVE_CALL_PROMPT_SENT_COPY,
                MessageKey.LIVE_CALL_PROMPT_SENT_COPY_TIP);
        expandedProperty().addListener((observed, was, now) -> sentBlock.refill());
        hideSection();
    }

    /**
     * Shows the parts of a prompt, or hides the view when there are none.
     *
     * @param sections the parts in the order they were sent, the system message's first
     */
    public void show(final List<PromptSection> sections) {
        show(sections, List.of(), Map.of());
    }

    /**
     * Shows the parts of a prompt and, in the raw block, the messages as sent.
     *
     * @param sections the parts in the order they were sent, the system message's first
     * @param messages the request's messages in wire order, or empty when the call site did not say
     * @param itemLocators the segment's location for a batch item id, for the ids it knows; empty when none is known
     */
    public void show(
            final List<PromptSection> sections,
            final List<ChatMessage> messages,
            final Map<String, String> itemLocators) {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(itemLocators, "itemLocators");
        if (sections.equals(parts) && messages.equals(sent) && itemLocators.equals(locators)) {
            return;
        }
        parts = sections;
        sent = messages;
        locators = itemLocators;
        render();
    }

    @Override
    void rename(final String id) {
        super.rename(id);
        sentBlock.rename(id + "-sent");
        render();
    }

    @Override
    String plainText() {
        return parts.isEmpty() ? plainSent() : plainParts();
    }

    private void render() {
        final boolean partless = parts.isEmpty();
        if (partless && sent.isEmpty()) {
            hideSection();
            cards.getChildren().clear();
            return;
        }
        showSection(
                partless
                        ? messages().get(MessageKey.LIVE_CALL_PROMPT_FULL_TITLE, sent.size())
                        : messages().get(MessageKey.LIVE_CALL_PROMPT, parts.size()));
        cards.getChildren().setAll(roleCards());
        sentBlock.display(messages().get(MessageKey.LIVE_CALL_PROMPT_FULL_TITLE, sent.size()), !sent.isEmpty());
        if (!sent.isEmpty()) {
            cards.getChildren().add(sentBlock);
        }
        if (partless != wasPartless) {
            sentBlock.setExpanded(partless);
            wasPartless = partless;
        }
    }

    private List<VBox> roleCards() {
        final PromptParts renderer = new PromptParts(messages(), locators);
        final Map<PromptSection.Origin, VBox> built = new LinkedHashMap<>();
        for (final PromptSection part : parts) {
            built.computeIfAbsent(part.origin(), origin -> new VBox(SPACING, roleHeading(originKey(origin))))
                    .getChildren()
                    .add(sectionOf(part, renderer));
        }
        return List.copyOf(built.values());
    }

    private Label roleHeading(final MessageKey key) {
        final Label heading = new Label(messages().get(key));
        heading.getStyleClass().addAll("context-heading", "prompt-role");
        return heading;
    }

    private TitledPane sectionOf(final PromptSection part, final PromptParts renderer) {
        final String id = getId() + "-" + part.slot();
        final TitledPane section = new TitledPane(headingOf(part), renderer.render(part, id + "-view"));
        section.setId(id);
        section.setAnimated(false);
        section.getStyleClass().add("context-section");
        return section;
    }

    private String plainSent() {
        final StringJoiner text = new StringJoiner(PLAIN_SEPARATOR);
        for (final ChatMessage message : sent) {
            text.add(divider(roleKey(message.role())));
            text.add(message.content());
        }
        return text.toString();
    }

    private String plainParts() {
        final StringJoiner text = new StringJoiner(PLAIN_SEPARATOR);
        PromptSection.@Nullable Origin shown = null;
        for (final PromptSection section : parts) {
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
