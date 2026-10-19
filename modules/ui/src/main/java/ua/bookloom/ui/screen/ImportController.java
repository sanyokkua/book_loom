package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.dialog.ConfirmDialog;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportGuard;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.LanguageWarning;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.StructureChecksViewModel;
import ua.bookloom.ui.state.StructureSegmentsViewModel;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The import screen: a drop zone and a file picker that both hand a file to the view model, and the area below them
 * that shows what the view model made of it. Once a book is open the drop zone shrinks to one line and the area holds
 * the book's details and facts and its structure with the segments of the part picked in it.
 *
 * <p>The view model outlives this controller, so it is observed through a weak listener held by the field below; the
 * browse button's handler captures this controller and keeps it reachable for as long as the screen is on show.
 */
@Slf4j
public final class ImportController {

    private static final String DROPZONE_ACTIVE = "dropzone-active";
    private static final String DROPZONE_COMPACT = "dropzone-compact";
    private static final double DROPZONE_COLUMN_GAP = 12;
    private static final double DROPZONE_ROW_GAP = 8;

    private final ImportViewModel viewModel;
    private final ImportGuard guard;
    private final Messages messages;
    private final Navigator navigator;
    private final LanguageNames names;
    private final ConfirmDialog confirm;
    private final WorkflowProgress progress;
    private final StateMirror mirror;
    private final CurrentProject project;
    private final ImportBookView bookView;
    private final ChangeListener<ImportState> onState = (observed, was, now) -> show(now);

    @FXML
    private GridPane dropzone;

    @FXML
    private Label dropzoneGlyph;

    @FXML
    private Label dropzoneTitle;

    @FXML
    private Label dropzoneOr;

    @FXML
    private Button browseButton;

    @FXML
    private Pane stateHost;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param viewModel the state this screen shows
     * @param guard what a chosen file goes through before it is opened, so a translation that can continue is not
     *     replaced unasked
     * @param messages the catalogue the built parts are worded from
     * @param navigator where Continue leads
     * @param names how a language tag is named on the card and in the warnings
     * @param confirm the question asked before Cancel drops a project that holds work
     * @param progress the steps already done, which tell whether the project holds work
     * @param mirror the run's state, since a run that began is work too
     * @param project the open book, whose structure the screen shows beside its details
     * @param checks the background checks the facts card reports
     * @param segments the listing of the picked part's segments
     * @param brief the brief, whose finished save makes the segment listing stale
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public ImportController(
            final ImportViewModel viewModel,
            final ImportGuard guard,
            final Messages messages,
            final Navigator navigator,
            final LanguageNames names,
            final ConfirmDialog confirm,
            final WorkflowProgress progress,
            final StateMirror mirror,
            final CurrentProject project,
            final StructureChecksViewModel checks,
            final StructureSegmentsViewModel segments,
            final BookBriefViewModel brief) {
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.names = Objects.requireNonNull(names, "names");
        this.project = Objects.requireNonNull(project, "project");
        this.bookView = new ImportBookView(messages, checks, segments, brief);
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
        guard.requestImport(paths.get(0));
    }

    @FXML
    void initialize() {
        log.debug(
                "building the import screen in state {}",
                viewModel.state().get().getClass().getSimpleName());
        // The card carries the book's title, author and file name, which are book content, so they go no higher than
        // TRACE.
        log.trace("import screen state {}", viewModel.state().get());
        Tips.install(messages, browseButton, MessageKey.IMPORT_BROWSE_TIP);
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
            guard.requestImport(picked.toPath());
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
        arrangeDropzone(state instanceof ImportState.Detected);
        stateHost.getChildren().setAll(nodesFor(state));
    }

    // One grid holds the four parts in both shapes, so the drop target, its handlers and its focus order do not change
    // when an open book asks for the room: the full box stacks the parts, the compact one lays them on a line.
    private void arrangeDropzone(final boolean compact) {
        log.debug("the drop zone is {}", compact ? "one line, since a book is open" : "the full box");
        final List<Node> parts = List.of(dropzoneGlyph, dropzoneTitle, dropzoneOr, browseButton);
        for (int index = 0; index < parts.size(); index++) {
            GridPane.setConstraints(
                    parts.get(index), compact ? index : 0, compact ? 0 : index, 1, 1, HPos.CENTER, VPos.CENTER);
        }
        dropzone.setHgap(compact ? DROPZONE_COLUMN_GAP : 0);
        dropzone.setVgap(compact ? 0 : DROPZONE_ROW_GAP);
        dropzone.getStyleClass().remove(DROPZONE_COMPACT);
        if (compact) {
            dropzone.getStyleClass().add(DROPZONE_COMPACT);
        }
    }

    private List<Node> nodesFor(final ImportState state) {
        return switch (state) {
            case ImportState.Idle idle -> List.of();
            case ImportState.Opening opening -> List.of(ImportViews.progress(messages, opening.fileName()));
            case ImportState.Detected detected -> detectedNodes(detected);
            case ImportState.DrmBlocked blocked -> withChooseAnother(ImportViews.drmBlocked(messages, blocked));
            case ImportState.Unsupported unsupported ->
                withChooseAnother(ImportViews.unsupported(messages, unsupported));
            case ImportState.Refused refused ->
                withChooseAnother(List.of(ImportViews.refusal(messages, refused.fileName(), refused.error())));
        };
    }

    private List<Node> detectedNodes(final ImportState.Detected detected) {
        final List<Node> nodes = new ArrayList<>();
        final LanguageWarning warning = detected.warning();
        if (warning != null) {
            nodes.add(ImportViews.warning(messages, names, warning));
        }
        final Node details = ImportCardView.card(messages, names, detected.card());
        final OpenedBook book = project.book().get();
        if (book == null) {
            log.debug("no book is open behind the detected state; only its details are shown");
            nodes.add(details);
        } else {
            nodes.addAll(bookView.build(details, book));
        }
        nodes.add(detectedFooter());
        return nodes;
    }

    private List<Node> withChooseAnother(final List<Node> parts) {
        final List<Node> nodes = new ArrayList<>(parts);
        nodes.add(StepFooter.of(
                null,
                new StepFooter.Action(
                        "import-choose-another",
                        messages.get(MessageKey.IMPORT_CHOOSE_ANOTHER),
                        messages.get(MessageKey.IMPORT_CHOOSE_ANOTHER_TIP),
                        "btn-secondary",
                        this::chooseFile)));
        return nodes;
    }

    private Node detectedFooter() {
        return StepFooter.of(
                new StepFooter.Action(
                        "import-cancel",
                        messages.get(MessageKey.IMPORT_CANCEL),
                        messages.get(MessageKey.IMPORT_CANCEL_TIP),
                        "btn-ghost",
                        this::onCancel),
                new StepFooter.Action(
                        "import-continue",
                        messages.get(MessageKey.IMPORT_CONTINUE),
                        messages.get(MessageKey.IMPORT_CONTINUE_TIP),
                        "btn-primary",
                        this::onContinue));
    }

    private void onCancel() {
        final boolean holdsWork = mirror.runState().get() != RunState.IDLE
                || progress.done().stream().anyMatch(step -> step != ViewNames.IMPORT);
        log.debug("cancel pressed, the project holds work: {}", holdsWork);
        if (holdsWork) {
            confirm.ask(ConfirmDialog.Question.DISCARD_PROJECT, viewModel::cancel);
        } else {
            viewModel.cancel();
        }
    }

    private void onContinue() {
        final Optional<ViewNames> next = navigator.nextAvailableStep(ViewNames.IMPORT);
        log.debug("continue pressed, the next step is {}", next);
        next.ifPresent(navigator::navigate);
    }
}
