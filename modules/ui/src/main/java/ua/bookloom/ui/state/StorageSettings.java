package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.StorageInfo;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * The Storage tab's facts about the saved projects and the backup copy it writes. The store is read and the copy is
 * written on the background executor; the properties and the actions are for the FX thread.
 */
@Slf4j
@Singleton
public final class StorageSettings {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final StorageInfo storage;
    private final ProjectService projects;
    private final FileRevealer revealer;
    private final FileChooserPort chooser;
    private final ErrorPresenter errors;
    private final Executor background;
    private final Executor fx;
    private final Clock clock;
    private final ObjectProperty<@Nullable Path> location = new SimpleObjectProperty<>();
    private final LongProperty sizeBytes = new SimpleLongProperty();
    private final SimpleIntegerProperty projectCount = new SimpleIntegerProperty(-1);
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private final StringProperty backupSaved = new SimpleStringProperty();
    private final BooleanProperty backupRunning = new SimpleBooleanProperty(false);

    /**
     * Creates the holder over the real pool and FX thread.
     *
     * @param storage names, measures and copies the saved work
     * @param projects counts the saved projects
     * @param revealer opens the folder in the file manager
     * @param chooser asks where the backup goes
     * @param errors shows why a backup failed
     * @param background where the store is read and the copy is written, never the FX thread
     */
    @Inject
    public StorageSettings(
            final StorageInfo storage,
            final ProjectService projects,
            final FileRevealer revealer,
            final FileChooserPort chooser,
            final ErrorPresenter errors,
            final @BackgroundExecutor ExecutorService background) {
        this(
                storage,
                projects,
                revealer,
                chooser,
                errors,
                background,
                Platform::runLater,
                Clock.system(ZoneId.systemDefault()));
    }

    StorageSettings(
            final StorageInfo storage,
            final ProjectService projects,
            final FileRevealer revealer,
            final FileChooserPort chooser,
            final ErrorPresenter errors,
            final Executor background,
            final Executor fx,
            final Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.revealer = Objects.requireNonNull(revealer, "revealer");
        this.chooser = Objects.requireNonNull(chooser, "chooser");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Reads the file, its size and the project count in the background and publishes them on the FX thread. */
    public void load() {
        log.debug("loading the storage facts");
        backupSaved.set(null);
        try {
            background.execute(() -> {
                final Path file = storage.location().orElse(null);
                final long size = storage.sizeBytes();
                final var listed = projects.list();
                final int count = listed.isErr() || listed.data() == null
                        ? -1
                        : listed.data().size();
                fx.execute(() -> publish(file, size, count));
            });
        } catch (RejectedExecutionException stopped) {
            log.warn("the storage facts could not be read: the background pool has stopped", stopped);
        }
    }

    private void publish(final @Nullable Path file, final long size, final int count) {
        location.set(file);
        sizeBytes.set(size);
        projectCount.set(count);
        loaded.set(true);
        log.debug("storage loaded file={} bytes={} projects={}", file == null ? null : file.getFileName(), size, count);
    }

    /** Opens the folder that holds the saved work in the file manager. FX thread only. */
    public void openFolder() {
        final Path file = location.get();
        final Path folder = file == null ? null : file.getParent();
        if (folder == null) {
            log.debug("open folder ignored: the work is kept in memory only");
            return;
        }
        log.info("opening the folder of the saved work");
        revealer.open(folder);
    }

    /** Asks where to write a copy of the saved work and writes it there; a cancelled dialog does nothing. */
    public void backup() {
        backup(() -> log.debug("backup written; nothing follows it"));
    }

    /**
     * As {@link #backup()}, and runs {@code onSaved} on the FX thread once the copy is written; never after a
     * cancelled dialog or a failed copy.
     *
     * @param onSaved what follows a written copy (Delete all data asks its question again)
     */
    public void backup(final Runnable onSaved) {
        Objects.requireNonNull(onSaved, "onSaved");
        if (backupRunning.get()) {
            log.debug("backup ignored: one is already being written");
            return;
        }
        final Path initial = Path.of(System.getProperty("user.home", "."))
                .resolve("bookloom-backup-" + LocalDateTime.now(clock).format(STAMP) + ".db");
        final Optional<Path> chosen = chooser.chooseToSave(null, FileChooserPort.FileKind.ANY, initial);
        if (chosen.isEmpty()) {
            log.debug("backup cancelled: no file chosen");
            return;
        }
        final Path target = chosen.get();
        log.info("backup requested file={}", target.getFileName());
        backupSaved.set(null);
        backupRunning.set(true);
        try {
            background.execute(() -> {
                final Result<Path> written = storage.backupTo(target);
                fx.execute(() -> report(target, written, onSaved));
            });
        } catch (RejectedExecutionException stopped) {
            log.warn("the backup could not be started: the background pool has stopped", stopped);
            backupRunning.set(false);
            errors.present(AppError.of(
                    ErrorCode.internal, "Backup failed", "The backup could not be started.", null, stopped));
        }
    }

    private void report(final Path target, final Result<Path> written, final Runnable onSaved) {
        backupRunning.set(false);
        final AppError error = written.error();
        if (error != null) {
            errors.present(error);
            return;
        }
        final Path name = target.getFileName();
        backupSaved.set(name == null ? target.toString() : name.toString());
        onSaved.run();
    }

    /** {@return the file the work is saved in, or {@code null} while unknown or when it is kept in memory only} */
    public ReadOnlyObjectProperty<@Nullable Path> locationProperty() {
        return location;
    }

    /** {@return the size of the saved work in bytes} */
    public ReadOnlyLongProperty sizeBytesProperty() {
        return sizeBytes;
    }

    /** {@return how many projects are saved, or -1 when they could not be counted} */
    public ReadOnlyIntegerProperty projectCountProperty() {
        return projectCount;
    }

    /** {@return the file name of the backup written last, or {@code null} before one is written or after the tab is entered again} */
    public ReadOnlyStringProperty backupSavedProperty() {
        return backupSaved;
    }

    /** {@return whether a backup is being written now} */
    public ReadOnlyBooleanProperty backupRunningProperty() {
        return backupRunning;
    }

    /** {@return whether the facts have arrived} */
    public ReadOnlyBooleanProperty loadedProperty() {
        return loaded;
    }
}
