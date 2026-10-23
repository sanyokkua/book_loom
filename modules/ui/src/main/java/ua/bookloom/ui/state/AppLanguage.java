package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.LanguageChoice;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.StoredLanguage;
import ua.bookloom.ui.i18n.SystemLanguage;

/**
 * The language choice of the Appearance tab. The window is shown in one language for its whole run, so a choice
 * applies after a restart; this holds the choice, keeps it stored, says whether it differs from the language on
 * screen and restarts the application on request. FX Application Thread only.
 */
@Slf4j
@Singleton
public final class AppLanguage {

    private final SettingsRepository settings;
    private final StoredLanguage stored;
    private final SystemLanguage system;
    private final Messages messages;
    private final AppRestart appRestart;
    private final Executor background;
    private final Executor fx;
    private final ObjectProperty<LanguageChoice> choice = new SimpleObjectProperty<>(LanguageChoice.SYSTEM);
    private final BooleanProperty restartNeeded = new SimpleBooleanProperty(false);
    private final BooleanProperty saveFailed = new SimpleBooleanProperty(false);
    private final BooleanProperty restarting = new SimpleBooleanProperty(false);
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private boolean started;
    private volatile boolean saveFailedAtRestart;

    /**
     * Creates the holder over the real store, pool and FX thread.
     *
     * @param settings where the choice is kept
     * @param stored the choice read at startup
     * @param system the operating system's language
     * @param messages the catalogue on screen
     * @param appRestart what starts the application again
     * @param background where the store is written, never the FX thread
     */
    @Inject
    public AppLanguage(
            final SettingsRepository settings,
            final StoredLanguage stored,
            final SystemLanguage system,
            final Messages messages,
            final AppRestart appRestart,
            final @BackgroundExecutor ExecutorService background) {
        this(settings, stored, system, messages, appRestart, background, Platform::runLater);
    }

    /**
     * The same, with the hop back to the FX thread supplied, so a test needs no toolkit.
     *
     * @param settings where the choice is kept
     * @param stored the choice read at startup
     * @param system the operating system's language
     * @param messages the catalogue on screen
     * @param appRestart what starts the application again
     * @param background where the store is written and the restart is asked
     * @param fx runs a task on the FX Application Thread
     */
    public AppLanguage(
            final SettingsRepository settings,
            final StoredLanguage stored,
            final SystemLanguage system,
            final Messages messages,
            final AppRestart appRestart,
            final Executor background,
            final Executor fx) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.stored = Objects.requireNonNull(stored, "stored");
        this.system = Objects.requireNonNull(system, "system");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.appRestart = Objects.requireNonNull(appRestart, "appRestart");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    /**
     * The choice held.
     *
     * @return the stored choice until a person picks another
     */
    public ReadOnlyObjectProperty<LanguageChoice> choiceProperty() {
        start();
        return choice;
    }

    /**
     * Whether the choice differs from the language on screen, so a restart would change it.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty restartNeededProperty() {
        start();
        return restartNeeded;
    }

    /**
     * Whether a restart is under way, so the button that asks for one should not be used again.
     *
     * @return a read-only property; false again when no new copy could be started
     */
    public ReadOnlyBooleanProperty restartingProperty() {
        return restarting;
    }

    /**
     * Whether the last choice could not be stored.
     *
     * @return a read-only property; the choice still holds until the app closes
     */
    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed;
    }

    /**
     * Holds a choice and stores it in the background.
     *
     * @param picked the choice; {@link LanguageChoice#SYSTEM} forgets the stored language
     */
    public void choose(final LanguageChoice picked) {
        Objects.requireNonNull(picked, "picked");
        start();
        log.debug("language choice {} -> {}", choice.get(), picked);
        choice.set(picked);
        updateRestartNeeded();
        lastWrite = afterPending(() -> store(picked));
    }

    /**
     * Restarts once every choice is stored. A choice that could not be stored would come back as the old language, so
     * nothing is restarted then; the failed save is already shown. Asking again while a restart is under way does
     * nothing.
     *
     * @param onFailure run on the FX thread when no new copy was started
     */
    public void restart(final Runnable onFailure) {
        Objects.requireNonNull(onFailure, "onFailure");
        if (restarting.get()) {
            log.debug("restart already under way; the second ask is ignored");
            return;
        }
        restarting.set(true);
        log.debug("restart asked; waiting for the stored choice");
        lastWrite = afterPending(() -> restartUnlessUnsaved(onFailure));
    }

    // Every task runs after the one before it, whether that one worked or not, so writes land in the order made.
    private CompletableFuture<Void> afterPending(final Runnable task) {
        return lastWrite.handle((done, failure) -> (Void) null).thenRunAsync(task, background);
    }

    private void restartUnlessUnsaved(final Runnable onFailure) {
        if (saveFailedAtRestart) {
            log.warn("restart skipped: the language choice could not be stored");
            fx.execute(() -> restarting.set(false));
            return;
        }
        boolean started = false;
        try {
            final Result<Boolean> asked = appRestart.restart();
            started = asked.isOk() && Boolean.TRUE.equals(asked.data());
        } catch (RuntimeException e) {
            log.error("restart failed", e);
        }
        if (!started) {
            log.debug("restart not started; telling the person");
            fx.execute(() -> {
                restarting.set(false);
                onFailure.run();
            });
        }
    }

    private void store(final LanguageChoice picked) {
        final Optional<String> tag = picked.tag();
        boolean failed;
        try {
            failed = tag.isPresent()
                    ? settings.put(AppSetting.LANGUAGE, tag.get()).isErr()
                    : settings.remove(AppSetting.LANGUAGE).isErr();
        } catch (RuntimeException e) {
            log.error("storing the language choice failed", e);
            failed = true;
        }
        saveFailedAtRestart = failed;
        final boolean shown = failed;
        if (failed) {
            log.warn("the language choice could not be stored; it holds until the app closes");
        }
        fx.execute(() -> saveFailed.set(shown));
    }

    private void updateRestartNeeded() {
        final String wanted = choice.get().resolve(system.current()).getLanguage();
        restartNeeded.set(!wanted.equals(messages.locale().getLanguage()));
    }

    private void start() {
        if (!started) {
            started = true;
            choice.set(LanguageChoice.of(stored.get()));
            updateRestartNeeded();
        }
    }
}
