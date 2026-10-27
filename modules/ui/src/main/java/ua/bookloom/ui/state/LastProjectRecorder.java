package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Keeps the id of the open project as {@code app.lastProject}, whichever screen opened it, so the next launch can
 * reopen it. Written off the FX thread because the store refuses it.
 */
@Slf4j
@Singleton
public final class LastProjectRecorder {

    private final SettingsRepository settings;
    private final Executor background;

    /**
     * Starts following the open project.
     *
     * @param project the holder whose open book is followed
     * @param settings where the id is kept
     * @param background where the store is written
     */
    @Inject
    public LastProjectRecorder(
            final CurrentProject project,
            final SettingsRepository settings,
            @BackgroundExecutor final ExecutorService background) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.background = Objects.requireNonNull(background, "background");
        Objects.requireNonNull(project, "project").book().addListener((observed, was, now) -> {
            if (now != null) {
                store(now.projectId());
            }
        });
    }

    private void store(final String projectId) {
        log.debug("last project {} is to be stored", projectId);
        background.execute(() -> write(projectId));
    }

    private void write(final String projectId) {
        try {
            final Result<String> stored = settings.put(AppSetting.LAST_PROJECT, projectId);
            if (stored.isErr()) {
                log.warn(
                        "the last project could not be stored: {}",
                        Objects.requireNonNull(stored.error()).code());
            } else {
                log.debug("last project {} stored", projectId);
            }
        } catch (Throwable thrown) {
            log.warn("the last project could not be stored", thrown);
        }
    }
}
