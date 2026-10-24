package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.nio.file.Files;
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
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.ToastOccasion;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportGuard;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportVerdicts;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.ProjectFilter;
import ua.bookloom.ui.state.ProjectsViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.StructureChecksViewModel;
import ua.bookloom.ui.state.StructureSegmentsViewModel;
import ua.bookloom.ui.state.TechnicalDetails;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The import screen: a drop zone and a file picker that both hand a file to the view model, and the area below them
 * that shows what the view model made of it: one verdict line, and for an open book the closed "About this book" fold
 * and the book's text in three panes. Once a book is open the drop zone shrinks to one line.
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

    private final Toasts toasts;
    private final ImportViewModel viewModel;
    private final ImportGuard guard;
    private final Messages messages;
    private final Navigator navigator;
    private final ConfirmDialog confirm;
    private final WorkflowProgress progress;
    private final StateMirror mirror;
    private final CurrentProject project;
    private final ImportBookView bookView;
    private final ImportVerdictView verdictView;
    private final ErrorPresenter errors;
    private final ProjectsViewModel projectsView;
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
     * @param names how a language tag is named in the fold
     * @param confirm the question asked before Cancel drops a project that holds work
     * @param progress the steps already done, which tell whether the project holds work
     * @param mirror the run's state, since a run that began is work too
     * @param project the open book, whose structure the screen shows beside its details
     * @param checks the background checks the facts card reports
     * @param segments the listing of the picked part's segments
     * @param brief the brief, whose finished save makes the segment listing stale
     * @param errors where a damaged file's Details shows the typed reason
     * @param projectsView the Projects list, searched for the book that is already stored
     * @param technical the switch that shows the checks' technical lines, the token counts and the groups
     * @param toasts where a dropped folder is refused at once
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
            final BookBriefViewModel brief,
            final ErrorPresenter errors,
            final ProjectsViewModel projectsView,
            final TechnicalDetails technical,
            final Toasts toasts) {
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.project = Objects.requireNonNull(project, "project");
        this.bookView = new ImportBookView(
                messages, Objects.requireNonNull(names, "names"), checks, segments, brief, technical.property());
        this.errors = Objects.requireNonNull(errors, "errors");
        this.projectsView = Objects.requireNonNull(projectsView, "projectsView");
        this.verdictView = new ImportVerdictView(
                messages,
                new ImportVerdictView.Actions(viewModel::cancel, this::showThem, this::chooseFile, this::showDetails));
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
        final Path first = paths.get(0);
        // A folder is no book: said at once, before the open-another question or a refusal from deep in the reader.
        if (Files.isDirectory(first)) {
            log.debug("drop refused: {} is a folder", first.getFileName());
            toasts.raise(ToastOccasion.IMPORT_FOLDER);
            return;
        }
        guard.requestImport(first);
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
        final List<Node> nodes = new ArrayList<>();
        ImportVerdicts.of(messages, state).ifPresent(verdict -> {
            log.info("import verdict {}", verdict.kind());
            nodes.add(verdictView.build(verdict, state));
        });
        if (state instanceof ImportState.Detected detected) {
            nodes.addAll(bookView.build(detected.card(), project.book().get()));
            nodes.add(detectedFooter());
        }
        return nodes;
    }

    private void showDetails() {
        if (viewModel.state().get() instanceof ImportState.Refused refused) {
            log.debug("details of the refusal of {} requested", refused.fileName());
            errors.present(refused.error());
        }
    }

    private void showThem() {
        if (viewModel.state().get() instanceof ImportState.AlreadyInProjects already) {
            log.info("showing the projects of the book {}", already.fileName());
            projectsView.search().set(already.bookTitle());
            projectsView.filter().set(ProjectFilter.ALL);
            navigator.navigate(ViewNames.PROJECTS);
        }
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
