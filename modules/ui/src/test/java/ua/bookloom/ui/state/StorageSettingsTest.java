package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.RecordingDestinationChooser;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ScriptedStorageInfo;

/** The Storage tab's facts are read in the background, and a backup goes where the person picks and nowhere else. */
class StorageSettingsTest {

    private static final Path COPY = Path.of("copies", "copy.db");
    private static final Path DB = Path.of("data", "BookLoom", "bookloom.db");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T08:05:09Z"), ZoneOffset.UTC);

    private final ScriptedStorageInfo storage = new ScriptedStorageInfo(DB, 1_572_864L);
    private final ScriptedProjectService projects = new ScriptedProjectService();
    private final RecordingFileRevealer revealer = new RecordingFileRevealer();
    private final RecordingDestinationChooser chooser = new RecordingDestinationChooser();
    private final RecordingErrorPresenter errors = new RecordingErrorPresenter();

    private StorageSettings settings() {
        return new StorageSettings(storage, projects, revealer, chooser, errors, Runnable::run, Runnable::run, CLOCK);
    }

    @Test
    void load_dbAndProjects_sizeAndCount() {
        projects.onDecided(Path.of("/a.epub"), 0);
        projects.onDecided(Path.of("/b.epub"), 0);
        projects.onDecided(Path.of("/c.epub"), 0);
        final StorageSettings settings = settings();

        settings.load();

        assertThat(settings.locationProperty().get()).isEqualTo(DB);
        assertThat(settings.sizeBytesProperty().get()).isEqualTo(1_572_864L);
        assertThat(settings.projectCountProperty().get()).isEqualTo(3);
        assertThat(settings.loadedProperty().get()).isTrue();
    }

    @Test
    void load_projectListFails_countIsMinusOne() {
        projects.failDecidedWith(AppError.of(ErrorCode.internal, "Storage failure", "no list"));
        final StorageSettings settings = settings();

        settings.load();

        assertThat(settings.projectCountProperty().get()).isEqualTo(-1);
        assertThat(settings.loadedProperty().get()).isTrue();
    }

    @Test
    void openFolder_dbFile_opensItsFolder() {
        final StorageSettings settings = settings();
        settings.load();

        settings.openFolder();

        assertThat(revealer.opened()).containsExactly(Path.of("data", "BookLoom"));
    }

    @Test
    void backup_cancelled_nothingWrittenNoError() {
        chooser.answer(null);
        final StorageSettings settings = settings();
        settings.load();

        settings.backup();

        assertThat(storage.backups()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(settings.backupSavedProperty().get()).isNull();
    }

    @Test
    void backup_chosen_backupToCalledAndSavedNameKept() {
        chooser.answer(COPY);
        final StorageSettings settings = settings();
        settings.load();

        settings.backup();

        assertThat(storage.backups()).containsExactly(COPY);
        assertThat(settings.backupSavedProperty().get()).isEqualTo("copy.db");
        assertThat(settings.backupRunningProperty().get()).isFalse();
    }

    @Test
    void backup_fails_errorPresented() {
        chooser.answer(COPY);
        final AppError failure = AppError.of(ErrorCode.internal, "Backup failed", "disk full");
        storage.failBackupWith(failure);
        final StorageSettings settings = settings();
        settings.load();

        settings.backup();

        assertThat(errors.presented()).containsExactly(failure);
        assertThat(settings.backupSavedProperty().get()).isNull();
        assertThat(settings.backupRunningProperty().get()).isFalse();
    }

    @Test
    void backup_asked_suggestsAStampedNameInTheHomeFolder() {
        chooser.answer(null);
        final StorageSettings settings = settings();
        settings.load();

        settings.backup();

        assertThat(chooser.initials()).hasSize(1);
        assertThat(chooser.initials().getFirst().getFileName().toString())
                .isEqualTo("bookloom-backup-20261023-080509.db");
    }

    // IF a second click started a second copy, THEN two writers would race for one file name.
    @Test
    void backup_clickedTwiceWhileRunning_oneCopyAndBusyUntilDone() {
        chooser.answer(COPY);
        final java.util.ArrayDeque<Runnable> pool = new java.util.ArrayDeque<>();
        final StorageSettings settings =
                new StorageSettings(storage, projects, revealer, chooser, errors, pool::add, Runnable::run, CLOCK);

        settings.backup();
        settings.backup();

        assertThat(settings.backupRunningProperty().get()).isTrue();
        assertThat(pool).hasSize(1);
        pool.poll().run();
        assertThat(storage.backups()).containsExactly(COPY);
        assertThat(settings.backupRunningProperty().get()).isFalse();
    }

    // IF a stopped pool threw out of load, THEN opening the tab at shutdown would raise an uncaught exception.
    @Test
    void load_poolStopped_noThrowAndStaysUnloaded() {
        final StorageSettings settings = new StorageSettings(
                storage,
                projects,
                revealer,
                chooser,
                errors,
                task -> {
                    throw new java.util.concurrent.RejectedExecutionException("stopped");
                },
                Runnable::run,
                CLOCK);

        settings.load();

        assertThat(settings.loadedProperty().get()).isFalse();
    }

    @Test
    void load_afterBackup_statusCleared() {
        chooser.answer(COPY);
        final StorageSettings settings = settings();
        settings.backup();

        settings.load();

        assertThat(settings.backupSavedProperty().get()).isNull();
    }
}
