package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;

/**
 * The import screen: a drop zone and a file picker that both hand a file to the view model, and the area below them
 * that shows what the view model made of it.
 *
 * <p>The view model outlives this controller, so it is observed through a weak listener held by the field below; the
 * browse button's handler captures this controller and keeps it reachable for as long as the screen is on show.
 */
@Slf4j
public final class ImportController {

    private static final String DROPZONE_ACTIVE = "dropzone-active";

    private final ImportViewModel viewModel;
    private final Messages messages;
    private final Navigator navigator;
    private final ChangeListener<ImportState> onState = (observed, was, now) -> show(now);

    @FXML
    private Pane dropzone;

    @FXML
    private Button browseButton;

    @FXML
    private Pane stateHost;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel the state this screen shows and the open it starts
     * @param messages the catalogue the built parts are worded from
     * @param navigator where Continue leads
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public ImportController(final ImportViewModel viewModel, final Messages messages, final Navigator navigator) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    /**
     * Opens the first of the files dropped on the screen and ignores the rest: one book is open at a time. Separate
     * from the drop handler because the headless platform cannot read a drag clipboard, so this is where a drop is
     * driven from a test.
     *
     * @param paths the dropped files; an empty list is ignored
     */
    public void openDropped(final List<Path> paths) {
        Objects.requireNonNull(paths, "paths");
        if (paths.isEmpty()) {
            log.debug("drop ignored: it carried no file");
            return;
        }
        if (paths.size() > 1) {
            log.debug("drop carried {} files; opening the first and ignoring the rest", paths.size());
        }
        viewModel.open(paths.get(0));
    }

    @FXML
    void initialize() {
        log.debug(
                "building the import screen in state {}",
                viewModel.state().get().getClass().getSimpleName());
        // The card carries the book's title, author and file name, which are book content, so they go no higher than
        // TRACE.
        log.trace("import screen state {}", viewModel.state().get());
        browseButton.setOnAction(event -> chooseFile());
        browseButton.disableProperty().bind(viewModel.opening());
        dropzone.setOnDragEntered(event -> markDropzone(isDroppable(event)));
        dropzone.setOnDragExited(event -> markDropzone(false));
        dropzone.setOnDragOver(this::onDragOver);
        dropzone.setOnDragDropped(this::onDropped);
        viewModel.state().addListener(new WeakChangeListener<>(onState));
        show(viewModel.state().get());
    }

    private boolean isDroppable(final DragEvent event) {
        return !viewModel.opening().get() && event.getDragboard().hasFiles();
    }

    private void markDropzone(final boolean active) {
        dropzone.getStyleClass().remove(DROPZONE_ACTIVE);
        if (active) {
            dropzone.getStyleClass().add(DROPZONE_ACTIVE);
        }
    }

    private void onDragOver(final DragEvent event) {
        if (isDroppable(event)) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void onDropped(final DragEvent event) {
        final Dragboard board = event.getDragboard();
        final boolean droppable = board.hasFiles();
        log.debug("drop received, carrying files: {}", droppable);
        markDropzone(false);
        if (droppable) {
            // java.io.File appears only here, where the Dragboard hands it over; it becomes a Path at once.
            openDropped(board.getFiles().stream().map(file -> file.toPath()).toList());
        }
        event.setDropCompleted(droppable);
        event.consume();
    }

    private void chooseFile() {
        log.debug("file chooser requested");
        final FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get(MessageKey.IMPORT_CHOOSER_TITLE));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(messages.get(MessageKey.IMPORT_CHOOSER_FILTER), patterns()));
        // java.io.File appears only as the chooser's answer; it becomes a Path at once (null when cancelled).
        final var picked = chooser.showOpenDialog(dropzone.getScene().getWindow());
        log.debug("file chooser answered with a file: {}", picked != null);
        if (picked != null) {
            viewModel.open(picked.toPath());
        }
    }

    private static List<String> patterns() {
        return Arrays.stream(BookFormat.values())
                .flatMap(format -> format.suffixes().stream())
                .map(suffix -> "*" + suffix)
                .toList();
    }

    private void show(final ImportState state) {
        log.debug("showing import state {}", state.getClass().getSimpleName());
        stateHost.getChildren().setAll(nodesFor(state));
    }

    private List<Node> nodesFor(final ImportState state) {
        return switch (state) {
            case ImportState.Idle idle -> List.of();
            case ImportState.Opening opening -> List.of(ImportViews.progress(messages, opening.fileName()));
            case ImportState.Detected detected ->
                List.of(
                        ImportViews.card(messages, detected.card()),
                        actions(continueButton(MessageKey.IMPORT_CONTINUE)));
            case ImportState.Refused refused ->
                List.of(
                        ImportViews.refusal(messages, refused.fileName(), refused.error()),
                        actions(chooseAnotherButton()));
            case ImportState.LanguageMismatch mismatch ->
                List.of(
                        ImportViews.mismatch(messages, mismatch.declaredLang(), mismatch.detectedLang()),
                        ImportViews.card(messages, mismatch.card()),
                        actions(continueButton(MessageKey.IMPORT_CONTINUE_ANYWAY)));
        };
    }

    private Button continueButton(final MessageKey label) {
        final Button button = new Button(messages.get(label));
        button.setId("import-continue");
        button.getStyleClass().add("btn-primary");
        button.setOnAction(event -> onContinue());
        return button;
    }

    private Button chooseAnotherButton() {
        final Button button = new Button(messages.get(MessageKey.IMPORT_CHOOSE_ANOTHER));
        button.setId("import-choose-another");
        button.getStyleClass().add("btn-secondary");
        button.setOnAction(event -> chooseFile());
        return button;
    }

    private static Node actions(final Button button) {
        final HBox row = new HBox(button);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void onContinue() {
        final Optional<ViewNames> next = navigator.nextAvailableStep(ViewNames.IMPORT);
        log.debug("continue pressed, the next step is {}", next);
        next.ifPresent(navigator::navigate);
    }
}
