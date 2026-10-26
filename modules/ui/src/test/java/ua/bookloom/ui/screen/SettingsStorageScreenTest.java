package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.DiagnosticLog;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.RecordingAppRestart;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingFileChooser;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ScriptedStorageInfo;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.state.DetailedLogSwitch;
import ua.bookloom.ui.state.FileChooserPort;
import ua.bookloom.ui.state.FileRevealer;
import ua.bookloom.ui.state.StorageSettings;

/** Settings › Storage & logs as the window builds it: where the work is saved, the backup and the detailed log. */
class SettingsStorageScreenTest extends SettingsScreenTestBase {

    private static final Path HOME = Path.of(System.getProperty("user.home", ""));
    private static final Path DB_FILE =
            HOME.resolve("Library").resolve("BookLoom").resolve("bookloom.db");
    private static final Path LOG_DIR = HOME.resolve("Logs").resolve("BookLoom");

    @TempDir
    Path dataDir;

    private final ScriptedStorageInfo storage = new ScriptedStorageInfo(DB_FILE, 1_572_864L);
    private final ScriptedProjectService projects = new ScriptedProjectService();
    private final RecordingErrorPresenter errors = new RecordingErrorPresenter();
    private RecordingAppRestart restart = new RecordingAppRestart();
    private @Nullable DiagnosticLog diagnosticLog;

    @Override
    void customize(final UiTestInjector.Builder builder) {
        projects.willList(List.of(summaryOf("one.epub"), summaryOf("two.epub"), summaryOf("three.epub")));
        final DiagnosticLog scripted = diagnosticLog == null
                ? new DiagnosticLog(LOG_DIR, false, dataDir.resolve("detailed-log.on"), null, false)
                : diagnosticLog;
        builder.storage(storage)
                .projects(projects)
                .diagnosticLog(scripted)
                .errorPresenter(errors)
                .restart(restart);
    }

    private static ua.bookloom.api.pipeline.ProjectSummary summaryOf(final String file) {
        return ProjectFixtures.summary(ScriptedProjectService.storedProjectOf(Path.of(file)), 0, null, null);
    }

    private void openStorage() throws TimeoutException {
        openSettings();
        final Tab tab = tabs().getTabs().stream()
                .filter(each -> "settings-tab-storage".equals(each.getId()))
                .findFirst()
                .orElseThrow();
        onFx(() -> tabs().getSelectionModel().select(tab));
        awaitFx(() ->
                injector.getInstance(StorageSettings.class).loadedProperty().get()
                        && injector.getInstance(DetailedLogSwitch.class)
                                .loadedProperty()
                                .get());
    }

    private void reopenWith(final DiagnosticLog scripted) {
        diagnosticLog = scripted;
        useLocale(java.util.Locale.ENGLISH);
    }

    // IF the path were shown whole, THEN a long folder would hide the file's name; IF the whole path could not be read
    // anywhere, THEN a person could not tell two folders apart.
    @Test
    void paths_opened_shortFormWithFullPathTooltip() throws TimeoutException {
        openStorage();

        assertThat(text("storage-saved-in-value")).isEqualTo("Library ▸ BookLoom ▸ bookloom.db");
        assertThat(Tips.explanationOf(required("storage-saved-in-value"))).contains(DB_FILE.toString());
        assertThat(text("storage-log-folder-value")).isEqualTo("Logs ▸ BookLoom");
        assertThat(Tips.explanationOf(required("storage-log-folder-value"))).contains(LOG_DIR.toString());
    }

    @Test
    void kept_opened_saysLast10Launches() throws TimeoutException {
        openStorage();

        assertThat(text("storage-kept-value")).isEqualTo("the last 10 launches");
    }

    @Test
    void keys_opened_saysNoKeyIsSaved() throws TimeoutException {
        openStorage();

        assertThat(text("storage-keys-hint"))
                .isEqualTo("Keys are never saved — only the names of the environment variables that hold them.");
    }

    @Test
    void size_opened_megabytesAndProjectCount() throws TimeoutException {
        openStorage();

        assertThat(text("storage-size-value")).isEqualTo("1.5 MB · 3 projects");
    }

    // IF the tab did not say what the switch does, THEN a person would expect the log to change at once.
    @Test
    void detailed_opened_saysAfterRestartAndStateNow() throws TimeoutException {
        openStorage();

        assertThat(text("storage-detailed-restart")).isEqualTo("Applies after BookLoom restarts.");
        assertThat(text("storage-detailed-now")).isEqualTo("Not written now.");
        assertThat(((ToggleSwitch) required("storage-detailed")).isSelected()).isFalse();
    }

    // IF the switch changed the log running now, THEN the file a person is reading would change under them.
    @Test
    void detailed_switchedOn_markerWrittenStillNotWrittenNow() throws TimeoutException {
        openStorage();

        onFx(() -> ((ToggleSwitch) required("storage-detailed")).setSelected(true));

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> Files.exists(dataDir.resolve("detailed-log.on")));
        assertThat(text("storage-detailed-now")).isEqualTo("Not written now.");
    }

    // IF the launch value were silent, THEN the switch would look ignored for no reason.
    @Test
    void detailed_envOverride_namesBookloomTraceFile() throws TimeoutException {
        reopenWith(new DiagnosticLog(LOG_DIR, false, dataDir.resolve("detailed-log.on"), "BOOKLOOM_TRACE_FILE", false));

        openStorage();

        assertThat(isShown("storage-detailed-override")).isTrue();
        assertThat(text("storage-detailed-override")).startsWith("BOOKLOOM_TRACE_FILE is set for this launch");
    }

    @Test
    void openFolder_clicked_revealerOpensDataFolder() throws TimeoutException {
        openStorage();

        onFx(() -> ((Button) required("storage-open-folder")).fire());

        assertThat(((RecordingFileRevealer) injector.getInstance(FileRevealer.class)).opened())
                .containsExactly(DB_FILE.getParent());
    }

    @Test
    void openLogs_clicked_revealerOpensLogFolder() throws TimeoutException {
        openStorage();

        onFx(() -> ((Button) required("storage-open-logs")).fire());

        assertThat(((RecordingFileRevealer) injector.getInstance(FileRevealer.class)).opened())
                .containsExactly(LOG_DIR);
    }

    @Test
    void bundle_clicked_chooserAsked() throws TimeoutException {
        openStorage();

        onFx(() -> ((Button) required("storage-bundle")).fire());

        assertThat(((RecordingFileChooser) injector.getInstance(FileChooserPort.class)).initials())
                .singleElement()
                .satisfies(initial -> assertThat(initial.getFileName().toString())
                        .matches("bookloom-diagnostics-\\d{8}-\\d{6}\\.zip"));
    }

    // IF nothing said where the copy went, THEN a person could not find the copy they asked for.
    @Test
    void backup_chosen_statusNamesFile() throws TimeoutException {
        openStorage();
        ((RecordingFileChooser) injector.getInstance(FileChooserPort.class))
                .answer(Path.of("copies").resolve("my-backup.db"));

        onFx(() -> ((Button) required("storage-backup")).fire());

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> isShown("storage-backup-status"));
        assertThat(text("storage-backup-status")).isEqualTo("Backup saved: my-backup.db");
        assertThat(storage.backups()).containsExactly(Path.of("copies").resolve("my-backup.db"));
    }

    // IF the status stayed after the tab was left and entered, THEN it would describe a copy from long ago.
    @Test
    void backup_statusShown_tabEnteredAgain_statusGone() throws TimeoutException {
        openStorage();
        ((RecordingFileChooser) injector.getInstance(FileChooserPort.class))
                .answer(Path.of("copies").resolve("my-backup.db"));
        onFx(() -> ((Button) required("storage-backup")).fire());
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> isShown("storage-backup-status"));

        onFx(() -> tabs().getSelectionModel().select(0));
        openStorage();

        assertThat(isShown("storage-backup-status")).isFalse();
    }

    @Test
    void bundle_chosen_statusNamesFile() throws Exception {
        final Path logs = Files.createDirectories(dataDir.resolve("logs"));
        Files.writeString(logs.resolve("bookloom-trace.log"), "trace\n");
        reopenWith(new DiagnosticLog(logs, false, dataDir.resolve("detailed-log.on"), null, false));
        openStorage();
        ((RecordingFileChooser) injector.getInstance(FileChooserPort.class)).answer(dataDir.resolve("report.zip"));

        onFx(() -> ((Button) required("storage-bundle")).fire());

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> isShown("storage-bundle-status"));
        assertThat(text("storage-bundle-status")).isEqualTo("Diagnostic bundle saved: report.zip");
    }

    // IF a failed backup were silent, THEN a person would believe a copy exists that does not.
    @Test
    void backup_fails_errorDialogShown() throws TimeoutException {
        storage.failBackupWith(AppError.of(ErrorCode.validation, "Backup not written", "A file is already there."));
        openStorage();
        ((RecordingFileChooser) injector.getInstance(FileChooserPort.class))
                .answer(Path.of("copies").resolve("taken.db"));

        onFx(() -> ((Button) required("storage-backup")).fire());

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> !errors.presented().isEmpty());
        assertThat(errors.presented()).extracting(AppError::title).containsExactly("Backup not written");
    }

    // IF the button only said "Open folder", THEN a person could not tell the data folder from the log folder.
    @Test
    void openFolder_opened_saysDataFolder() throws TimeoutException {
        openStorage();

        assertThat(text("storage-open-folder")).isEqualTo("Open data folder");
    }

    // IF the question did not say what goes and what stays, THEN a person could fear for the books they chose.
    @Test
    void deleteAll_clicked_asksWhatIsDeletedAndWhatStays() throws TimeoutException {
        openStorage();

        onFx(() -> ((Button) required("storage-delete-all")).fire());

        assertThat(cardText())
                .contains("All projects, translations, glossaries, providers and settings are deleted")
                .contains("The book files you chose, the files you exported and the logs are not touched.")
                .doesNotContain("is stopped first");
        assertThat(isShown("delete-data-backup")).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> ((Button) required("delete-data-cancel")).isDefaultButton()))
                .isTrue();
    }

    // IF Cancel deleted anything, THEN the safe answer would not be safe.
    @Test
    void deleteAll_cancelled_nothingDeletedNoRestart() throws TimeoutException {
        openStorage();
        onFx(() -> ((Button) required("storage-delete-all")).fire());

        onFx(() -> ((Button) required("delete-data-cancel")).fire());

        WaitForAsyncUtils.waitForFxEvents();
        assertThat(storage.deletes()).isZero();
        assertThat(restart.calls()).isZero();
        assertThat(errors.presented()).isEmpty();
    }

    @Test
    void deleteAll_confirmed_deletesThenRestarts() throws TimeoutException {
        openStorage();
        onFx(() -> ((Button) required("storage-delete-all")).fire());

        onFx(() -> ((Button) required("delete-data-yes")).fire());

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> restart.calls() == 1);
        assertThat(storage.deletes()).isEqualTo(1);
        assertThat(errors.presented()).isEmpty();
    }

    // IF the backup were written and the question forgotten, THEN a person would have to find the button again.
    @Test
    void deleteAll_backupFirst_writesTheCopyThenAsksAgainWithoutDeleting() throws TimeoutException {
        openStorage();
        ((RecordingFileChooser) injector.getInstance(FileChooserPort.class))
                .answer(Path.of("copies").resolve("before-delete.db"));
        onFx(() -> ((Button) required("storage-delete-all")).fire());

        onFx(() -> ((Button) required("delete-data-backup")).fire());

        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> isShown("delete-data-card"));
        assertThat(storage.backups()).containsExactly(Path.of("copies").resolve("before-delete.db"));
        assertThat(text("storage-backup-status")).isEqualTo("Backup saved: before-delete.db");
        assertThat(storage.deletes()).isZero();
        assertThat(restart.calls()).isZero();
    }

    // IF a failed delete were silent or named no folder, THEN a person could not fix the permission that stopped it.
    @Test
    void deleteAll_deleteFails_errorNamesTheDataFolderAndNoRestart() throws TimeoutException {
        storage.failDeleteWith(
                AppError.of(ErrorCode.internal, "Data not deleted", "The data folder could not be changed."));
        openStorage();
        onFx(() -> ((Button) required("storage-delete-all")).fire());

        onFx(() -> ((Button) required("delete-data-yes")).fire());

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> !errors.presented().isEmpty());
        assertThat(errors.presented()).singleElement().satisfies(error -> {
            assertThat(error.title()).isEqualTo("Data not deleted");
            assertThat(error.message())
                    .contains(HOME.resolve("Library").resolve("BookLoom").toString())
                    .contains("Your data is kept.");
        });
        assertThat(restart.calls()).isZero();
        assertThat(ThemeTestSupport.onFx(() -> required("storage-delete-all").isDisabled()))
                .isFalse();
    }

    // IF a copy that cannot restart itself said nothing, THEN the window would sit on deleted data with no way on.
    @Test
    void deleteAll_restartNotPossible_saysCloseAndOpenAgain() throws TimeoutException {
        restart = new RecordingAppRestart(Result.ok(false));
        useLocale(java.util.Locale.ENGLISH);
        openStorage();
        onFx(() -> ((Button) required("storage-delete-all")).fire());

        onFx(() -> ((Button) required("delete-data-yes")).fire());

        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> !errors.presented().isEmpty());
        assertThat(errors.presented())
                .extracting(AppError::message)
                .containsExactly(
                        "All data is deleted. BookLoom could not start again by itself: close it and open it again.");
        assertThat(ThemeTestSupport.onFx(() -> required("storage-delete-all").isDisabled()))
                .isTrue();
    }

    private String cardText() {
        return ThemeTestSupport.onFx(() -> required("delete-data-card").lookupAll(".label").stream()
                .map(node -> ((Labeled) node).getText())
                .collect(Collectors.joining("\n")));
    }
}
