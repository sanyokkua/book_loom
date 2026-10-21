package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The one "Show technical details" switch. Every Simple/Detailed view, Advanced fold and failure card reads this
 * property instead of keeping a flag of its own, so the technical lines appear in one place and nowhere else. FX
 * Application Thread only; the value is remembered as {@code ui.technicalDetails}.
 */
@Slf4j
@Singleton
public final class TechnicalDetails {

    private final BooleanProperty enabled = new SimpleBooleanProperty(false);
    private final SettingsRepository settings;
    private final Executor background;
    private boolean loading;
    private boolean askedFor;

    /**
     * Starts off (FX thread only), reads the stored switch in the background (the store refuses the FX thread) and keeps it stored from
     * then on.
     *
     * @param settings the store the switch is remembered in
     * @param background where the store is read and written
     */
    @Inject
    public TechnicalDetails(final SettingsRepository settings, final @BackgroundExecutor ExecutorService background) {
        this(settings, background, Platform::runLater);
    }

    /**
     * The same, with the hop back to the FX thread supplied, so a test needs no toolkit.
     *
     * @param settings the store the switch is remembered in
     * @param background where the store is read and written
     * @param fx runs a task on the FX Application Thread
     */
    public TechnicalDetails(final SettingsRepository settings, final Executor background, final Executor fx) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.background = Objects.requireNonNull(background, "background");
        Objects.requireNonNull(fx, "fx");
        enabled.addListener((observable, was, now) -> changed(now));
        background.execute(() -> {
            final boolean stored = read();
            fx.execute(() -> loaded(stored));
        });
    }

    private void changed(final boolean now) {
        if (!loading) {
            askedFor = true;
            store(now);
        }
    }

    // A person's own choice made before the stored value arrived wins over it.
    private void loaded(final boolean stored) {
        if (askedFor) {
            log.debug("technical details: the person chose before the stored switch arrived, keeping that choice");
            return;
        }
        loading = true;
        enabled.set(stored);
        loading = false;
    }

    private boolean read() {
        final var read = settings.get(AppSetting.TECHNICAL_DETAILS);
        final var stored = read.data();
        if (stored == null) {
            log.warn("technical details: the stored switch could not be read, starting off");
            return false;
        }
        final boolean on = stored.orElse(false);
        log.info("technical details start {}", on ? "on" : "off");
        return on;
    }

    private void store(final boolean now) {
        log.debug("technical details switched {}", now ? "on" : "off");
        background.execute(() -> {
            if (settings.put(AppSetting.TECHNICAL_DETAILS, now).isErr()) {
                log.warn("technical details: the switch could not be stored, it holds until the app closes");
            }
        });
    }

    /**
     * The switch. A view reads it (or binds a fold's expanded state to it) when it is built; the Settings toggle sets
     * it.
     *
     * @return the property; never null
     */
    public BooleanProperty property() {
        return enabled;
    }

    /**
     * Whether technical lines are shown now.
     *
     * @return {@code true} if the switch is on, {@code false} otherwise
     */
    public boolean isOn() {
        return enabled.get();
    }
}
