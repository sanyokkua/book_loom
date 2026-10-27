package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.ModelSwitch;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.DataDeletion;
import ua.bookloom.ui.state.DetailedLogSwitch;
import ua.bookloom.ui.state.DiagnosticActions;
import ua.bookloom.ui.state.FileSizes;
import ua.bookloom.ui.state.ShortPath;
import ua.bookloom.ui.state.StorageSettings;
import ua.bookloom.util.paths.AppPaths;

/**
 * The Storage &amp; logs area of the settings screen: where the work is saved and how big it is, the backup copy,
 * deleting all data, the log folder and the switch for the detailed diagnostic log, which applies from the next
 * launch.
 *
 * <p>The state outlives this controller, so it is observed through bindings that hold it weakly.
 */
@Slf4j
public final class StorageController {

    private final StorageSettings storage;
    private final DataDeletion deletion;
    private final DetailedLogSwitch detailedLog;
    private final DiagnosticActions diagnostics;
    private final Messages messages;
    private final Path home = Path.of(System.getProperty("user.home", ""));

    @FXML
    private VBox columns;

    @FXML
    private VBox projectsCard;

    @FXML
    private VBox logsCard;

    @FXML
    private Label savedIn;

    @FXML
    private Label size;

    @FXML
    private Button openFolder;

    @FXML
    private Button backup;

    @FXML
    private Label backupStatus;

    @FXML
    private Button deleteAll;

    @FXML
    private Label bundleStatus;

    @FXML
    private Label logFolder;

    @FXML
    private Label kept;

    @FXML
    private ToggleSwitch detailed;

    @FXML
    private Label detailedNow;

    @FXML
    private Label detailedOverride;

    @FXML
    private Label detailedDev;

    @FXML
    private Button openLogs;

    @FXML
    private Button bundle;

    @FXML
    private Label saveError;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param storage the saved work's file, size and backup
     * @param deletion deletes all the saved work and restarts
     * @param detailedLog the detailed log switch
     * @param diagnostics opens the log folder and saves the diagnostic bundle
     * @param messages the catalogue the built nodes are worded from
     */
    // The FXML loader assigns the labelled fields after construction, which NullAway cannot see.
    @SuppressWarnings("NullAway.Init")
    @Inject
    public StorageController(
            final StorageSettings storage,
            final DataDeletion deletion,
            final DetailedLogSwitch detailedLog,
            final DiagnosticActions diagnostics,
            final Messages messages) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.deletion = Objects.requireNonNull(deletion, "deletion");
        this.detailedLog = Objects.requireNonNull(detailedLog, "detailedLog");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @FXML
    void initialize() {
        log.debug("building the storage area");
        final TwoColumns pair = new TwoColumns(projectsCard, logsCard, LayoutClass.Width.REGULAR);
        pair.setId("storage-two-columns");
        columns.getChildren().setAll(pair);
        buildProjects();
        buildLogs();
    }

    private void buildProjects() {
        final ObservableValue<String> where = Bindings.createStringBinding(
                () -> savedInText(storage.locationProperty().get()),
                storage.locationProperty(),
                storage.loadedProperty());
        savedIn.textProperty().bind(where);
        Tips.installLive(
                savedIn,
                Bindings.createStringBinding(
                        () -> {
                            final Path file = storage.locationProperty().get();
                            return file == null ? "" : file.toString();
                        },
                        storage.locationProperty()));
        size.textProperty()
                .bind(Bindings.createStringBinding(
                        this::sizeText,
                        storage.sizeBytesProperty(),
                        storage.projectCountProperty(),
                        storage.loadedProperty()));
        buildProjectActions();
    }

    private void buildProjectActions() {
        final var noFile = storage.locationProperty().isNull();
        openFolder.disableProperty().bind(noFile);
        backup.disableProperty().bind(noFile.or(storage.backupRunningProperty()));
        deleteAll.disableProperty().bind(noFile.or(deletion.deletingProperty()));
        savedLine(backupStatus, storage.backupSavedProperty(), MessageKey.SETTINGS_STORAGE_BACKUP_SAVED);
        openFolder.setOnAction(event -> storage.openFolder());
        backup.setOnAction(event -> storage.backup());
        deleteAll.setOnAction(event -> deletion.ask());
        Tips.install(messages, openFolder, MessageKey.SETTINGS_STORAGE_OPEN_FOLDER_TIP);
        Tips.install(messages, backup, MessageKey.SETTINGS_STORAGE_BACKUP_TIP);
        Tips.install(messages, deleteAll, MessageKey.SETTINGS_STORAGE_DELETE_ALL_TIP);
    }

    private String savedInText(final @Nullable Path file) {
        if (!storage.loadedProperty().get()) {
            return "";
        }
        return file == null
                ? messages.get(MessageKey.SETTINGS_STORAGE_MEMORY_ONLY)
                : ShortPath.of(file, home).text();
    }

    private String sizeText() {
        if (!storage.loadedProperty().get()) {
            return "";
        }
        final String bytes = FileSizes.format(storage.sizeBytesProperty().get(), messages);
        final int count = storage.projectCountProperty().get();
        return count < 0 ? bytes : messages.get(MessageKey.SETTINGS_STORAGE_SIZE_VALUE, bytes, count);
    }

    private void buildLogs() {
        final Path directory = diagnostics.diagnosticLog().directory();
        logFolder.setText(ShortPath.of(directory, home).text());
        Tips.install(logFolder, directory.toString());
        kept.setText(messages.get(MessageKey.SETTINGS_STORAGE_KEPT_VALUE, AppPaths.RUN_LOGS_KEPT));
        ModelSwitch.bind(detailed, detailedLog.onProperty(), detailedLog::choose);
        Tips.install(messages, detailed, MessageKey.SETTINGS_STORAGE_DETAILED_TIP);
        detailedNow.setText(messages.get(
                detailedLog.runningNow()
                        ? MessageKey.SETTINGS_STORAGE_DETAILED_ON
                        : MessageKey.SETTINGS_STORAGE_DETAILED_OFF));
        showOverride();
        Visibility.bind(detailedDev, detailedLog.onProperty().map(on -> detailedLog.developmentAlways()));
        Visibility.bind(saveError, detailedLog.saveFailedProperty());
        savedLine(bundleStatus, diagnostics.bundleSavedProperty(), MessageKey.ABOUT_BUNDLE_SAVED);
        openLogs.setOnAction(event -> diagnostics.openFolder());
        bundle.setOnAction(event -> diagnostics.saveBundle());
        Tips.install(messages, openLogs, MessageKey.ABOUT_OPEN_LOG_FOLDER_TIP);
        Tips.install(messages, bundle, MessageKey.ABOUT_SAVE_BUNDLE_TIP);
    }

    // The result of an action stays in place under its buttons until the next action or the next visit to the tab.
    private void savedLine(final Label line, final ObservableValue<String> fileName, final MessageKey wording) {
        line.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> fileName.getValue() == null ? "" : messages.get(wording, fileName.getValue()), fileName));
        Visibility.bind(line, fileName.map(name -> name != null));
    }

    private void showOverride() {
        final String name = detailedLog.decidedBy();
        detailedOverride.setText(name == null ? "" : messages.get(MessageKey.SETTINGS_STORAGE_DETAILED_OVERRIDE, name));
        detailedOverride.setVisible(name != null);
        detailedOverride.setManaged(name != null);
    }
}
