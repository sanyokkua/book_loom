package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * What the window does once it is shown: with "Reopen the last project" on and that project still stored it opens it
 * where its row says, and otherwise stays on Projects. A project the person opened in the meantime is left alone.
 */
@Slf4j
@Singleton
public final class LaunchProject {

    private final ProjectService projects;
    private final SettingsRepository settings;
    private final CurrentProject current;
    private final ProjectOpener opener;
    private final ExecutorService executor;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param projects the service the stored projects are listed through
     * @param settings where the switch and the last project's id are read
     * @param current the holder that tells whether a project is already open
     * @param opener what opens the last project
     * @param executor the daemon executor the stores are read on, never the FX thread
     */
    @Inject
    public LaunchProject(
            final ProjectService projects,
            final SettingsRepository settings,
            final CurrentProject current,
            final ProjectOpener opener,
            @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.current = Objects.requireNonNull(current, "current");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Decides at launch what to show. Returns at once; the decision is applied on the FX thread. FX thread only. */
    public void openAtLaunch() {
        log.debug("launch: deciding between the last project and Projects");
        executor.execute(() -> {
            final Optional<ProjectRow> row = lastProjectRow();
            Platform.runLater(() -> apply(row));
        });
    }

    private Optional<ProjectRow> lastProjectRow() {
        try {
            final boolean reopen = read(AppSetting.REOPEN_LAST_PROJECT).orElse(true);
            final Optional<String> last = read(AppSetting.LAST_PROJECT);
            log.debug("launch: reopen switch {}, last project stored {}", reopen, last.isPresent());
            if (!reopen || last.isEmpty()) {
                return Optional.empty();
            }
            final Result<List<ProjectSummary>> listed = projects.list();
            final List<ProjectSummary> all = listed.data();
            if (all == null) {
                log.warn(
                        "launch: the projects could not be listed: {}",
                        Objects.requireNonNull(listed.error()).code());
                return Optional.empty();
            }
            return all.stream()
                    .filter(summary -> summary.project().id().equals(last.get()))
                    .findFirst()
                    .flatMap(summary -> ProjectRows.of(List.of(summary), null, Map.of()).stream()
                            .flatMap(group -> group.rows().stream())
                            .findFirst());
        } catch (Throwable thrown) {
            log.warn("launch: the last project could not be read", thrown);
            return Optional.empty();
        }
    }

    private <T> Optional<T> read(final AppSetting<T> setting) {
        final Optional<T> stored = settings.get(setting).data();
        return stored == null ? Optional.empty() : stored;
    }

    private void apply(final Optional<ProjectRow> row) {
        if (current.book().get() != null) {
            log.info("launch: showing Projects (a project is already open)");
        } else if (row.isPresent()) {
            log.info(
                    "launch: reopening project {}",
                    row.get().summary().project().id());
            opener.open(row.get());
        } else {
            log.info("launch: showing Projects (nothing to reopen)");
        }
    }
}
