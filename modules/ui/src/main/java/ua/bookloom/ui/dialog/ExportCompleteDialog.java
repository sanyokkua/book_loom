package ua.bookloom.ui.dialog;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.FileSizes;

/**
 * The card that reports a written book: the file, its folder, the verification result and the size, with Open folder
 * and Open book. It is the export's one success notice, so nothing else announces the same success. Shown in the
 * shell's modal host like the About card, so the stylesheet and the active theme block reach it.
 */
@Slf4j
@Singleton
public final class ExportCompleteDialog {

    static final String CARD_ID = "export-complete-card";
    static final String CLOSE_ID = "export-complete-close";
    static final String FOLDER_ID = "export-complete-folder";
    static final String OPEN_ID = "export-complete-open";

    private final ModalHost modalHost;
    private final Messages messages;
    private final FileRevealer revealer;

    /**
     * Creates the dialog.
     *
     * @param modalHost the shared overlay the card is shown in
     * @param messages the catalogue every word of the card comes from
     * @param revealer what Open folder and Open book ask of the system
     */
    @Inject
    public ExportCompleteDialog(final ModalHost modalHost, final Messages messages, final FileRevealer revealer) {
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.revealer = Objects.requireNonNull(revealer, "revealer");
    }

    /**
     * Shows the card for a finished export. FX thread only.
     *
     * @param outcome the report and the size of the written file
     */
    public void show(final ExportOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        final Path file = outcome.report().destination();
        log.debug("showing the export-complete card for {}", file);
        final DialogPane card = new ModalCard();
        card.setId(CARD_ID);
        card.getStyleClass().addAll("dialog-card", "elevation-lg");
        final Label title = new Label(messages.get(MessageKey.EXPORT_COMPLETE_TITLE));
        title.getStyleClass().add("dialog-title");
        final VBox header = new VBox(title);
        header.getStyleClass().add("dialog-h");
        card.setHeader(header);
        card.setContent(body(file, outcome.sizeBytes()));
        buttons(card, file);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        modalHost.show(card, false);
    }

    private Node body(final Path file, final long sizeBytes) {
        final Label written = new Label(messages.get(MessageKey.EXPORT_COMPLETE_WRITTEN, file.getFileName()));
        written.setWrapText(true);
        written.getStyleClass().add("dialog-text");
        final Path folder = file.getParent();
        final Label verified = new Label(messages.get(MessageKey.EXPORT_COMPLETE_VERIFIED));
        verified.getStyleClass().add("chip-ok");
        return new VBox(
                written,
                row(MessageKey.EXPORT_COMPLETE_LOCATION, plain(folder == null ? "" : folder.toString())),
                row(MessageKey.EXPORT_COMPLETE_VALIDATION, verified),
                row(MessageKey.EXPORT_COMPLETE_SIZE, plain(FileSizes.format(sizeBytes, messages))));
    }

    private static Label plain(final String text) {
        final Label value = new Label(text);
        value.getStyleClass().add("kv-value");
        return value;
    }

    private Node row(final MessageKey label, final Label value) {
        final Label key = new Label(messages.get(label));
        key.getStyleClass().add("kv-key");
        final HBox row = new HBox(key, value);
        row.getStyleClass().add("kv");
        return row;
    }

    private void buttons(final DialogPane card, final Path file) {
        final ButtonType close =
                new ButtonType(messages.get(MessageKey.COMMON_CLOSE), ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType folder = new ButtonType(messages.get(MessageKey.EXPORT_REVEAL), ButtonBar.ButtonData.OTHER);
        final ButtonType open = new ButtonType(messages.get(MessageKey.EXPORT_OPEN_BOOK), ButtonBar.ButtonData.OK_DONE);
        card.getButtonTypes().setAll(close, folder, open);
        wire((Button) card.lookupButton(close), CLOSE_ID, "btn-ghost", path -> {}, file);
        wire((Button) card.lookupButton(folder), FOLDER_ID, "btn-secondary", revealer::reveal, file);
        wire((Button) card.lookupButton(open), OPEN_ID, "btn-primary", revealer::open, file);
    }

    private void wire(
            final Button button,
            final String id,
            final String styleClass,
            final Consumer<Path> action,
            final Path file) {
        button.setId(id);
        button.getStyleClass().add(styleClass);
        button.setOnAction(event -> {
            log.debug("export-complete button {} pressed", id);
            modalHost.hide();
            action.accept(file);
        });
    }
}
