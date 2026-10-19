package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.SectionMemory;

/**
 * A collapsed, quiet titled section with a body and Copy above it, which puts what the section shows on the clipboard
 * as plain text. The one look of every "what the model was given" section: the context of a segment in the review
 * panel, and the prompt and the reply of a call in the live panel.
 *
 * <p>Copy builds its text when it is pressed, never before: a live call is shown far more often than it is copied. The
 * section stays collapsed or open as the person left it while what it shows changes. Showing logs nothing, as it is
 * redrawn on every live change; only a press of Copy is logged.
 */
@Slf4j
public abstract class CopyablePane extends TitledPane {

    static final double BODY_MAX = ScrollingBody.BODY_MAX;

    private static final double SPACING = 6;

    private final Messages messages;
    private final Consumer<String> clipboard;
    private final Region body;
    private final Button copy;
    private @Nullable SectionMemory memory;

    /**
     * Builds a hidden, collapsed section.
     *
     * @param id the section's node id; the body is {@code <id>-body} and Copy is {@code <id>-copy}
     * @param messages the catalogue the controls are worded from
     * @param clipboard where Copy puts the plain text
     * @param tip the hover explanation of the section
     * @param copyLabel the label of Copy
     * @param copyTip the hover explanation of Copy
     * @param body what the section shows when it is open
     */
    CopyablePane(
            final String id,
            final Messages messages,
            final Consumer<String> clipboard,
            final MessageKey tip,
            final MessageKey copyLabel,
            final MessageKey copyTip,
            final Region body) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.body = Objects.requireNonNull(body, "body");
        this.copy = Tips.install(messages, new Button(messages.get(copyLabel)), copyTip);
        getStyleClass().add("context-section");
        setExpanded(false);
        setAnimated(false);
        body.getStyleClass().add("context-body");
        copy.getStyleClass().add("btn-ghost");
        copy.setOnAction(event -> copyNow());
        setContent(new VBox(SPACING, copyRow(), body));
        Tips.install(messages, this, tip);
        assignIds(Objects.requireNonNull(id, "id"));
    }

    /**
     * Keeps this section open or closed as the person last left a section with the same id this session, and records
     * each later change there, so a screen rebuilt on the next visit does not close it.
     *
     * @param sections the session's memory of open sections
     */
    public void rememberIn(final SectionMemory sections) {
        memory = Objects.requireNonNull(sections, "sections");
        setExpanded(sections.isOpen(getId()));
        expandedProperty().addListener((observed, was, now) -> sections.remember(getId(), now));
    }

    /**
     * Gives the section and its parts new ids, as a block that moves to another role does, and opens or closes it as
     * the person last left a section of the new id.
     *
     * @param id the section's new node id
     */
    void rename(final String id) {
        Objects.requireNonNull(id, "id");
        assignIds(id);
        final SectionMemory sections = memory;
        if (sections != null) {
            setExpanded(sections.isOpen(id));
        }
    }

    /** What Copy puts on the clipboard, built when it is asked for. */
    abstract String plainText();

    final Messages messages() {
        return messages;
    }

    final void showSection(final String title) {
        setVisible(true);
        setManaged(true);
        setText(title);
    }

    final void hideSection() {
        setVisible(false);
        setManaged(false);
        setText("");
    }

    private void assignIds(final String id) {
        setId(id);
        body.setId(id + "-body");
        copy.setId(id + "-copy");
    }

    private void copyNow() {
        final String text = plainText();
        log.debug("copying the context of {} ({} characters)", getId(), text.length());
        clipboard.accept(text);
    }

    private HBox copyRow() {
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
}
