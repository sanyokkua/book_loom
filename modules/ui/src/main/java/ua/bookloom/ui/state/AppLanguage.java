package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.Result;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.SettingWriter;
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

    /** How a restart asked for ended. */
    private enum Restart {
        STARTED,
        UNSAVED,
        FAILED
    }

    private final SettingWriter writer;
    private final StoredLanguage stored;
    private final SystemLanguage system;
    private final Messages messages;
    private final AppRestart appRestart;
    private final ObjectProperty<LanguageChoice> choice = new SimpleObjectProperty<>(LanguageChoice.SYSTEM);
    private final BooleanProperty restartNeeded = new SimpleBooleanProperty(false);
    private final BooleanProperty restarting = new SimpleBooleanProperty(false);
    private boolean started;

    /**
     * Creates the holder.
     *
     * @param writer stores the choice and asks the restart off the FX thread, in the order they were made
     * @param stored the choice read at startup
     * @param system the operating system's language
     * @param messages the catalogue on screen
     * @param appRestart what starts the application again
     */
    @Inject
    public AppLanguage(
            final SettingWriter writer,
            final StoredLanguage stored,
            final SystemLanguage system,
            final Messages messages,
            final AppRestart appRestart) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.stored = Objects.requireNonNull(stored, "stored");
        this.system = Objects.requireNonNull(system, "system");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.appRestart = Objects.requireNonNull(appRestart, "appRestart");
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
        return writer.saveFailedProperty();
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
        final Optional<String> tag = picked.tag();
        if (tag.isPresent()) {
            writer.put(AppSetting.LANGUAGE, tag.get());
        } else {
            writer.remove(AppSetting.LANGUAGE);
        }
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
        writer.afterWrites(this::restartUnlessUnsaved, outcome -> {
            if (outcome != Restart.STARTED) {
                restarting.set(false);
            }
            if (outcome == Restart.FAILED) {
                onFailure.run();
            }
        });
    }

    private Restart restartUnlessUnsaved() {
        if (writer.lastWriteFailed()) {
            log.warn("restart skipped: the language choice could not be stored");
            return Restart.UNSAVED;
        }
        try {
            final Result<Boolean> asked = appRestart.restart();
            if (asked.isOk() && Boolean.TRUE.equals(asked.data())) {
                return Restart.STARTED;
            }
        } catch (RuntimeException e) {
            log.error("restart failed", e);
        }
        log.debug("restart not started; telling the person");
        return Restart.FAILED;
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
