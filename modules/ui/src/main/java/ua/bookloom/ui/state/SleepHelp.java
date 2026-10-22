package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The help line about sleep and keep-awake on the Translating screen: shown until the person closes it for the open
 * project, and worded by whether keep-awake is on. Both settings are read off the FX thread when a project opens; the
 * closing is written there too. A read that finishes after another project was opened is dropped. FX thread only,
 * except the reads and the write.
 */
@Slf4j
@Singleton
public final class SleepHelp {

    private final SettingsRepository settings;
    private final Executor background;
    private final Executor fx;
    private final CurrentProject current;
    private final ReadOnlyBooleanWrapper shown = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper keepAwake =
            new ReadOnlyBooleanWrapper(Boolean.TRUE.equals(AppSetting.KEEP_AWAKE.defaultValue()));

    /**
     * Follows the open project, reading on the background executor.
     *
     * @param settings the stored settings
     * @param background where the settings are read and written
     * @param current the open project
     */
    @Inject
    public SleepHelp(
            final SettingsRepository settings,
            final @BackgroundExecutor ExecutorService background,
            final CurrentProject current) {
        this(settings, background, Platform::runLater, current);
    }

    /**
     * Follows the open project with the given executors, as a test needs.
     *
     * @param settings the stored settings
     * @param background where the settings are read and written
     * @param fx where what was read is shown
     * @param current the open project
     */
    public SleepHelp(
            final SettingsRepository settings,
            final Executor background,
            final Executor fx,
            final CurrentProject current) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.current = Objects.requireNonNull(current, "current");
        current.book().addListener((observed, was, now) -> read());
        read();
    }

    /** {@return whether the line is shown for the open project; FX thread only} */
    public ReadOnlyBooleanProperty shown() {
        return shown.getReadOnlyProperty();
    }

    /** {@return whether keep-awake is on, which words the line; FX thread only} */
    public ReadOnlyBooleanProperty keepAwake() {
        return keepAwake.getReadOnlyProperty();
    }

    /** Hides the line and stores that for the open project. FX thread only. */
    public void close() {
        shown.set(false);
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("sleep help closed with no project open: nothing stored");
            return;
        }
        final String projectId = book.projectId();
        log.info("sleep help line closed for project {}", projectId);
        background.execute(() -> {
            if (settings.put(AppSetting.sleepLineClosed(projectId), true).isErr()) {
                log.warn(
                        "sleep help: closing for project {} could not be stored, it holds until the app closes",
                        projectId);
            }
        });
    }

    private void read() {
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("sleep help: no project open, the line is hidden");
            shown.set(false);
            return;
        }
        final String projectId = book.projectId();
        background.execute(() -> {
            final boolean closed = valueOf(AppSetting.sleepLineClosed(projectId), false);
            final boolean awake = valueOf(AppSetting.KEEP_AWAKE, true);
            log.debug("sleep help: project {} closed {}, keep-awake {}", projectId, closed, awake);
            fx.execute(() -> show(projectId, closed, awake));
        });
    }

    private void show(final String projectId, final boolean closed, final boolean awake) {
        final OpenedBook book = current.book().get();
        if (book == null || !book.projectId().equals(projectId)) {
            log.debug("sleep help: the read of project {} came after another project opened; dropped", projectId);
            return;
        }
        shown.set(!closed);
        keepAwake.set(awake);
    }

    private boolean valueOf(final AppSetting<Boolean> setting, final boolean fallback) {
        final Optional<Boolean> stored = settings.get(setting).data();
        if (stored == null) {
            log.warn("sleep help: the setting {} could not be read, using {}", setting.key(), fallback);
            return fallback;
        }
        return stored.orElse(fallback);
    }
}
