package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FileChooserPort;
import ua.bookloom.ui.state.ShortPath;

/**
 * Save as: the destination as a short path with the full path on hover, Change… and ✦ Suggest a name beside it, and
 * under it the line that an existing file is asked about, the name notices, and the refusal or the note about the file
 * just exported.
 *
 * <p>The path is chosen, never typed: Change… opens the save dialog and a suggested name is used only when the person
 * presses "Use this name". The view model outlives this card, so the bindings below are the only links to it; the
 * screen keeps the card alive for as long as its nodes are shown.
 */
@Slf4j
final class ExportDestinationCard {

    private static final double ROW_SPACING = 8;

    private final ExportViewModel viewModel;
    private final Messages messages;
    private final FileChooserPort chooser;
    private final Path home;

    ExportDestinationCard(final ExportViewModel viewModel, final Messages messages, final FileChooserPort chooser) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.home = Path.of(System.getProperty("user.home", ""));
    }

    Node build() {
        final Label path = new Label();
        path.setId("export-save-to");
        path.getStyleClass().add("export-save-to");
        path.textProperty().bind(Bindings.createStringBinding(() -> shortPath().text(), viewModel.destination()));
        Tips.installLive(path, viewModel.destination());
        HBox.setHgrow(path, Priority.ALWAYS);
        path.setMaxWidth(Double.MAX_VALUE);
        // The folders give way first; the home note and the buttons keep their words whole.
        path.setMinWidth(0);
        final Label inHome = new Label(messages.get(MessageKey.EXPORT_SAVE_TO_HOME));
        inHome.setId("export-save-to-home");
        inHome.getStyleClass().add("hint");
        inHome.setMinWidth(Region.USE_PREF_SIZE);
        Visibility.bind(inHome, Bindings.createBooleanBinding(() -> shortPath().inHome(), viewModel.destination()));
        final HBox row = new HBox(ROW_SPACING, path, inHome, changeButton());
        if (viewModel.nameSuggestion.isOffered()) {
            row.getChildren().add(suggestButton());
        }
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(
                ROW_SPACING,
                BriefCards.field(messages, MessageKey.EXPORT_SAVE_TO, row),
                BriefCards.hint(messages, MessageKey.EXPORT_REPLACE_HINT),
                nameNotice(),
                authorNote(),
                refusal(),
                currentNote());
    }

    // The folders under home and the file name; a path inside home says so beside it, since home is not shown.
    private ShortPath shortPath() {
        return viewModel
                .destinationPath()
                .map(chosen -> ShortPath.of(chosen, home))
                .orElse(new ShortPath(List.of(), "", false));
    }

    private Button changeButton() {
        final Button change = new Button(messages.get(MessageKey.EXPORT_BROWSE));
        change.setId("export-browse");
        Tips.install(messages, change, MessageKey.EXPORT_BROWSE_TIP);
        change.getStyleClass().add("btn-secondary");
        change.setMinWidth(Region.USE_PREF_SIZE);
        change.setOnAction(event -> change());
        return change;
    }

    private Button suggestButton() {
        final Button suggest = new Button(messages.get(MessageKey.EXPORT_SUGGEST_NAME));
        suggest.setId("export-suggest-name");
        Tips.install(messages, suggest, MessageKey.EXPORT_SUGGEST_NAME_TIP);
        suggest.getStyleClass().add("btn-secondary");
        suggest.setMinWidth(Region.USE_PREF_SIZE);
        suggest.disableProperty().bind(viewModel.nameSuggestion.suggesting());
        suggest.setOnAction(event -> viewModel.nameSuggestion.ask());
        return suggest;
    }

    private Node nameNotice() {
        final Label notice = BriefCards.hint(messages, MessageKey.EXPORT_SUGGEST_NAME_DONE);
        notice.setId("export-name-notice");
        notice.textProperty().bind(viewModel.nameSuggestion.notice());
        return Visibility.bind(notice, viewModel.nameSuggestion.notice().isNotEmpty());
    }

    private Node authorNote() {
        final Label note = BriefCards.hint(messages, MessageKey.EXPORT_SUGGEST_NAME_AUTHOR_KEPT);
        note.setId("export-author-kept");
        return Visibility.bind(note, viewModel.nameSuggestion.authorKept());
    }

    private Node refusal() {
        final Label refusal = new Label();
        refusal.setId("export-refusal");
        refusal.setWrapText(true);
        refusal.getStyleClass().add("status-err");
        refusal.textProperty().bind(viewModel.refusal());
        return Visibility.bind(refusal, viewModel.refusal().isNotEmpty());
    }

    // Not an error: the result above is current, and exporting again only asks before replacing it.
    private Node currentNote() {
        final Label note = new Label();
        note.setId("export-current-note");
        note.setWrapText(true);
        note.getStyleClass().add("hint");
        note.textProperty().bind(viewModel.currentNote());
        return Visibility.bind(note, viewModel.currentNote().isNotEmpty());
    }

    private void change() {
        final Path initial = viewModel.destinationPath().orElse(Path.of(""));
        log.debug("change pressed, the dialog starts at {}", initial);
        chooser.chooseToSave(null, FileChooserPort.FileKind.ANY, initial).ifPresent(viewModel::chooseDestination);
    }
}
