package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.AbstractModule;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.StorageSettings;

/**
 * Delete all data in the real window over the SQLite file, with a translation paused: the question says the run is
 * stopped first, the run ends before anything is deleted, the database files are gone when the restart is asked, and
 * the window opened again on the same folder starts on a fresh database. The process restart itself is a fake that
 * records what it found.
 */
// `slow`: an import and a run in the real window, then a second window.
@Tag("slow")
class DeleteAllDataTest extends WorkspaceTestBase {

    private static final String[] DATABASE_FILES = {"bookloom.db", "bookloom.db-wal", "bookloom.db-shm"};

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final List<String> atRestart = new CopyOnWriteArrayList<>();

    @AfterEach
    void detach() {
        root().detachAppender(logged);
    }

    @Test
    void deleteAll_translationPaused_stopsRunDeletesFilesAndRestartsFresh() throws Exception {
        pausedRun(recordingRestart());
        assertThat(injector.getInstance(SettingsRepository.class)
                        .put(AppSetting.FIRST_LAUNCH_PROBED, true)
                        .isOk())
                .isTrue();
        logged.start();
        root().addAppender(logged);

        openStorageTab();
        onFx(() -> button("#storage-delete-all").fire());
        waitUntil(() -> shell.root().lookup("#delete-data-card") != null);
        // The card's body reaches the scene once its scroll host has a skin; the line is then there or never.
        waitUntil(() -> shell.root().lookup("#delete-data-running") != null);
        assertThat(runState()).as("nothing changes before the answer").isEqualTo(RunState.PAUSED);
        onFx(() -> button("#delete-data-yes").fire());

        waitUntil(() -> !atRestart.isEmpty());
        assertThat(atRestart).containsExactly("run ended, files gone");
        assertThat(DATABASE_FILES)
                .allSatisfy(name -> assertThat(dataDir.resolve(name)).doesNotExist());
        assertThat(dataDir.resolve("logs")).as("the logs stay").isDirectory();
        assertThat(logged.list)
                .as("no write fails once the run is stopped and the files are gone")
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .isEmpty();

        assertFreshAfterRestart();
    }

    // The restart opens the same folder again: what it finds is a new, empty database.
    private void assertFreshAfterRestart() throws Exception {
        startStoredWindow();
        assertThat(injector.getInstance(ProjectRepository.class).all().data()).isEmpty();
        assertThat(injector.getInstance(ProviderConfigs.class).all())
                .as("the providers are gone with the rest")
                .noneMatch(config -> config.id().equals(PseudoOnlyModule.PSEUDO.id()));
        assertThat(injector.getInstance(SettingsRepository.class)
                        .get(AppSetting.FIRST_LAUNCH_PROBED)
                        .data())
                .as("the first-launch look runs once more")
                .isEmpty();
    }

    // What the launcher would do next happens only after this answer: the fake records the state it was asked in.
    private com.google.inject.Module recordingRestart() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(AppRestart.class).toInstance(() -> {
                    final boolean ended = injector.getInstance(StateMirror.class)
                            .runState()
                            .get()
                            .isIdleOrEnded();
                    final boolean gone =
                            List.of(DATABASE_FILES).stream().noneMatch(name -> Files.exists(dataDir.resolve(name)));
                    atRestart.add("run " + (ended ? "ended" : "at work") + ", files " + (gone ? "gone" : "left"));
                    return Result.ok(true);
                });
            }
        };
    }

    private void openStorageTab() throws Exception {
        onFx(() -> shell.activate(ViewNames.SETTINGS));
        onFx(() -> {
            final TabPane tabs = (TabPane) Objects.requireNonNull(shell.root().lookup("#settings-tabs"));
            final Tab storage = tabs.getTabs().stream()
                    .filter(tab -> "settings-tab-storage".equals(tab.getId()))
                    .findFirst()
                    .orElseThrow();
            tabs.getSelectionModel().select(storage);
        });
        waitUntil(() ->
                injector.getInstance(StorageSettings.class).loadedProperty().get());
    }

    private Button button(final String selector) {
        return (Button) Objects.requireNonNull(shell.root().lookup(selector));
    }

    private static Logger root() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
