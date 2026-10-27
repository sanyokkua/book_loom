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
 * project, and worded by whether keep-awake is on, which it mirrors from {@link KeepAwakeGate}. The closing is read off the FX
 * thread when a project opens and written there too. A read that finishes after another project was opened is dropped. FX thread only,
 * except the reads and the write.
 */
@Slf4j
@Singleton
public final class SleepHelp {

    private final SettingsRepository settings;
    private final Executor background;
    private final Executor fx;
    private final CurrentProject current;
    private final KeepAwakeGate gate;
    private final ReadOnlyBooleanWrapper shown = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper keepAwake = new ReadOnlyBooleanWrapper(true);

    /**
     * Follows the open project, reading on the background executor.
     *
     * @param settings the stored settings
     * @param background where the settings are read and written
     * @param current the open project
     * @param gate the person's keep-awake choice, which words the line
     */
    @Inject
    public SleepHelp(
            final SettingsRepository settings,
            final @BackgroundExecutor ExecutorService background,
            final CurrentProject current,
            final KeepAwakeGate gate) {
        this(settings, background, Platform::runLater, current, gate);
    }

    /**
     * Follows the open project with the given executors, as a test needs.
     *
     * @param settings the stored settings
     * @param background where the settings are read and written
     * @param fx where what was read is shown
     * @param current the open project
     * @param gate the person's keep-awake choice, which words the line
     */
    public SleepHelp(
            final SettingsRepository settings,
            final Executor background,
            final Executor fx,
            final CurrentProject current,
            final KeepAwakeGate gate) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.current = Objects.requireNonNull(current, "current");
        this.gate = Objects.requireNonNull(gate, "gate");
        keepAwake.set(gate.isOn());
        gate.onChange(() -> keepAwake.set(gate.isOn()));
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
            log.debug("sleep help: project {} closed {}", projectId, closed);
            fx.execute(() -> show(projectId, closed));
        });
    }

    private void show(final String projectId, final boolean closed) {
        final OpenedBook book = current.book().get();
        if (book == null || !book.projectId().equals(projectId)) {
            log.debug("sleep help: the read of project {} came after another project opened; dropped", projectId);
            return;
        }
        // The stored choice is seeded into the gate after this object may have been built, so look again.
        keepAwake.set(gate.isOn());
        shown.set(!closed);
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
