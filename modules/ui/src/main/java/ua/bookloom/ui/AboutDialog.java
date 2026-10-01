package ua.bookloom.ui;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.ModalCard;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.DiagnosticActions;

/**
 * The About card: the product, its licence and the build version, in the shape the reference gives every dialog
 * (header, body, footer), and the diagnostic log — whether the detailed one is on, where the logs are, and the actions
 * that open, copy and bundle them for a report.
 *
 * <p>A {@link DialogPane} placed in the {@link ModalHost} rather than a {@code Dialog} in its own window: the pane is
 * the control the reference's dialog maps to, and staying inside the scene keeps the stylesheet and the active theme
 * block, which a second window would not inherit.
 */
final class AboutDialog {

    static final String CARD_ID = "about-card";
    static final String CLOSE_ID = "about-close";
    static final String LOG_STATE_ID = "about-log-state";
    static final String OPEN_FOLDER_ID = "about-open-log-folder";
    static final String COPY_PATH_ID = "about-copy-log-path";
    static final String SAVE_BUNDLE_ID = "about-save-bundle";

    private final Messages messages;
    private final String version;
    private final DiagnosticActions diagnostics;
    private final Runnable onClose;

    /**
     * Prepares the card's content.
     *
     * @param messages the catalogue every word is drawn from
     * @param version the build version to report; {@code dev} in a build carrying no release version
     * @param diagnostics the log settings and what the diagnostic actions do
     * @param onClose what the Close button does
     */
    AboutDialog(
            final Messages messages,
            final String version,
            final DiagnosticActions diagnostics,
            final Runnable onClose) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.version = Objects.requireNonNull(version, "version");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
    }

    /**
     * Builds a fresh card, so a second opening never inherits state from the first.
     *
     * @return the card's root node, ready for {@link ModalHost#show(Node, boolean)}
     */
    Node card() {
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        card.setHeader(header());
        card.setContent(body());
        card.getButtonTypes().add(ButtonType.CLOSE);
        final Button close = (Button) card.lookupButton(ButtonType.CLOSE);
        close.setId(CLOSE_ID);
        Tips.install(messages, close, MessageKey.COMMON_CLOSE_TIP);
        close.setText(messages.get(MessageKey.COMMON_CLOSE));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(event -> onClose.run());
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return card;
    }

    private Node header() {
        final Label title = new Label(messages.get(MessageKey.SHELL_TITLE));
        title.getStyleClass().add("dialog-title");
        final Label subtitle = new Label(messages.get(MessageKey.ABOUT_SUBTITLE, version));
        subtitle.getStyleClass().add("dialog-sub");
        final VBox header = new VBox(title, subtitle);
        header.getStyleClass().add("dialog-h");
        return header;
    }

    private Node body() {
        final Label description = text(messages.get(MessageKey.ABOUT_DESCRIPTION), "dialog-text");
        return new VBox(description, diagnosticSection());
    }

    // The state is stated plainly, with the one way to change it, because nothing in the window can change it yet.
    private Node diagnosticSection() {
        final Label heading = text(messages.get(MessageKey.ABOUT_LOG_HEADING), "dialog-title");
        final boolean detailed = diagnostics.diagnosticLog().detailed();
        final Label state =
                text(messages.get(detailed ? MessageKey.ABOUT_LOG_ON : MessageKey.ABOUT_LOG_OFF), "dialog-text");
        state.setId(LOG_STATE_ID);
        final Label privacy = text(messages.get(MessageKey.ABOUT_LOG_PRIVACY), "hint");
        final Label folder = text(
                messages.get(
                        MessageKey.ABOUT_LOG_FOLDER,
                        diagnostics.diagnosticLog().directory().toString()),
                "hint");
        final VBox section = new VBox(heading, state, privacy, folder, actions());
        section.getStyleClass().add("about-diagnostics");
        return section;
    }

    private Node actions() {
        final FlowPane actions = new FlowPane(
                action(
                        OPEN_FOLDER_ID,
                        MessageKey.ABOUT_OPEN_LOG_FOLDER,
                        MessageKey.ABOUT_OPEN_LOG_FOLDER_TIP,
                        diagnostics::openFolder),
                action(
                        COPY_PATH_ID,
                        MessageKey.ABOUT_COPY_LOG_PATH,
                        MessageKey.ABOUT_COPY_LOG_PATH_TIP,
                        diagnostics::copyPath),
                action(
                        SAVE_BUNDLE_ID,
                        MessageKey.ABOUT_SAVE_BUNDLE,
                        MessageKey.ABOUT_SAVE_BUNDLE_TIP,
                        diagnostics::saveBundle));
        actions.getStyleClass().add("about-actions");
        return actions;
    }

    private Button action(final String id, final MessageKey label, final MessageKey tip, final Runnable onPress) {
        final Button button = new Button(messages.get(label));
        button.setId(id);
        button.getStyleClass().add("btn-secondary");
        Tips.install(messages, button, tip);
        button.setOnAction(event -> onPress.run());
        return button;
    }

    private static Label text(final String value, final String styleClass) {
        final Label label = new Label(value);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
