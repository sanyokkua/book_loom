package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SectionMemory;

/**
 * A collapsed, quiet titled section whose body is headed blocks of wrapped text in a scrolling area, with Copy above
 * it putting the same as plain text on the clipboard. The one look of every "what the model was given" section: the
 * context of a segment in the review panel and the prompt of a call in the live panel.
 *
 * <p>Opened, the body takes the height of what it shows at the section's width, up to {@value #BODY_MAX} pixels, and
 * scrolls inside past that. It stays collapsed or open as the person left it while what it shows changes. Showing
 * logs nothing, as it is redrawn on every live change; only a press of Copy is logged.
 */
@Slf4j
public abstract class CopyablePane extends TitledPane {

    static final double BODY_MAX = ScrollingBody.BODY_MAX;

    private static final double SPACING = 6;
    private static final double SECTION_SPACING = 10;

    private final Messages messages;
    private final Consumer<String> clipboard;
    private final VBox sections = new VBox(SECTION_SPACING);
    private String plain = "";

    /**
     * Builds a hidden, collapsed section.
     *
     * @param id the section's node id; the body is {@code <id>-body} and Copy is {@code <id>-copy}
     * @param messages the catalogue the controls are worded from
     * @param clipboard where Copy puts the plain text
     * @param tip the hover explanation of the section
     * @param copy the label of Copy
     * @param copyTip the hover explanation of Copy
     */
    CopyablePane(
            final String id,
            final Messages messages,
            final Consumer<String> clipboard,
            final MessageKey tip,
            final MessageKey copy,
            final MessageKey copyTip) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        setId(id);
        getStyleClass().add("context-section");
        setExpanded(false);
        setAnimated(false);
        sections.getStyleClass().add("context-sections");
        final ScrollingBody body = new ScrollingBody(sections);
        body.setId(id + "-body");
        body.getStyleClass().add("context-body");
        setContent(new VBox(SPACING, copyRow(id, copy, copyTip), body));
        Tips.install(messages, this, tip);
    }

    /**
     * Keeps this section open or closed as the person last left a section with the same id this session, and records
     * each later change there, so a screen rebuilt on the next visit does not close it.
     *
     * @param memory the session's memory of open sections
     */
    public void rememberIn(final SectionMemory memory) {
        Objects.requireNonNull(memory, "memory");
        setExpanded(memory.isOpen(getId()));
        expandedProperty().addListener((observed, was, now) -> memory.remember(getId(), now));
    }

    /** What Copy puts on the clipboard. */
    final String plainText() {
        return plain;
    }

    final Messages messages() {
        return messages;
    }

    /** Shows {@code parts} under {@code title}, or hides the section when {@code parts} is {@code null}. */
    final void display(final String title, final String plainText, final List<Node> parts) {
        setVisible(true);
        setManaged(true);
        setText(title);
        plain = plainText;
        sections.getChildren().setAll(parts);
    }

    final void hideSection() {
        setVisible(false);
        setManaged(false);
        setText("");
        plain = "";
        sections.getChildren().clear();
    }

    private HBox copyRow(final String id, final MessageKey label, final MessageKey copyTip) {
        final Button copy = Tips.install(messages, new Button(messages.get(label)), copyTip);
        copy.setId(id + "-copy");
        copy.getStyleClass().add("btn-ghost");
        copy.setOnAction(event -> {
            log.debug("copying the context of {} ({} characters)", getId(), plain.length());
            clipboard.accept(plain);
        });
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox row = new HBox(spacer, copy);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    static void toClipboard(final String text) {
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    static Label text(final String styleClass, final String value) {
        final Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    /** A headed block: the heading above its nodes. */
    static VBox block(final String heading, final List<? extends Node> nodes) {
        final Label title = new Label(heading);
        title.getStyleClass().add("context-heading");
        final VBox block = new VBox(SPACING, title);
        block.getChildren().addAll(nodes);
        return block;
    }
}
