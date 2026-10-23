package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.GenerationOverrides;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The Generation tab's two stored choices, thinking and sampling, and the variable or property that overrides each
 * for this launch. A choice is stored the moment it is made and reaches the segments not translated yet, because a
 * run reads the settings when it starts. FX Application Thread only.
 */
@Slf4j
@Singleton
public final class GenerationSettings {

    private final SettingsRepository settings;
    private final GenerationOverrides overrides;
    private final Executor background;
    private final Executor fx;
    private final ObjectProperty<ReasoningSetting> reasoning = new SimpleObjectProperty<>(ReasoningSetting.OFF);
    private final ObjectProperty<SamplingSetting> sampling =
            new SimpleObjectProperty<>(SamplingSetting.PROGRAM_DEFAULTS);
    private final StringProperty reasoningOverride = new SimpleStringProperty();
    private final StringProperty samplingOverride = new SimpleStringProperty();
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private final BooleanProperty saveFailed = new SimpleBooleanProperty(false);
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private boolean reasoningChosen;
    private boolean samplingChosen;

    /**
     * Creates the holder over the real store, pool and FX thread.
     *
     * @param settings where the choices are kept
     * @param overrides names what overrides a choice for this launch
     * @param background where the store is read and written, never the FX thread
     */
    @Inject
    public GenerationSettings(
            final SettingsRepository settings,
            final GenerationOverrides overrides,
            final @BackgroundExecutor ExecutorService background) {
        this(settings, overrides, background, Platform::runLater);
    }

    /**
     * The same, with the hop back to the FX thread supplied, so a test needs no toolkit.
     *
     * @param settings where the choices are kept
     * @param overrides names what overrides a choice for this launch
     * @param background where the store is read and written
     * @param fx runs a task on the FX Application Thread
     */
    public GenerationSettings(
            final SettingsRepository settings,
            final GenerationOverrides overrides,
            final Executor background,
            final Executor fx) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.background = Objects.requireNonNull(background, "background");
        this.fx = Objects.requireNonNull(fx, "fx");
    }

    /** Reads both stored choices and the overrides in the background and publishes them on the FX thread. */
    public void load() {
        log.debug("loading the generation settings");
        background.execute(() -> {
            final ReasoningSetting storedReasoning = read(AppSetting.REASONING);
            final SamplingSetting storedSampling = read(AppSetting.SAMPLING);
            final String reasoningSource = overrides.reasoning().orElse(null);
            final String samplingSource = overrides.sampling().orElse(null);
            fx.execute(() -> publish(storedReasoning, storedSampling, reasoningSource, samplingSource));
        });
    }

    private <T> T read(final AppSetting<T> setting) {
        final T fallback = Objects.requireNonNull(setting.defaultValue(), "default");
        final var read = settings.get(setting);
        final var value = read.data();
        if (value == null) {
            log.warn("generation: {} could not be read; showing its default", setting.key());
            return fallback;
        }
        return value.orElse(fallback);
    }

    // A choice made before the stored values arrived wins over them.
    private void publish(
            final ReasoningSetting storedReasoning,
            final SamplingSetting storedSampling,
            @Nullable final String reasoningSource,
            @Nullable final String samplingSource) {
        if (!reasoningChosen) {
            reasoning.set(storedReasoning);
        }
        if (!samplingChosen) {
            sampling.set(storedSampling);
        }
        reasoningOverride.set(reasoningSource);
        samplingOverride.set(samplingSource);
        loaded.set(true);
        log.debug(
                "generation loaded reasoning={} sampling={} overrides={}/{}",
                reasoning.get(),
                sampling.get(),
                reasoningSource,
                samplingSource);
    }

    /**
     * Holds and stores a thinking level.
     *
     * @param level the level; never null
     */
    public void choose(final ReasoningSetting level) {
        Objects.requireNonNull(level, "level");
        log.debug("thinking choice {} -> {}", reasoning.get(), level);
        reasoning.set(level);
        reasoningChosen = true;
        store(AppSetting.REASONING, level);
    }

    /**
     * Holds and stores a sampling mode.
     *
     * @param mode the mode; never null
     */
    public void choose(final SamplingSetting mode) {
        Objects.requireNonNull(mode, "mode");
        log.debug("sampling choice {} -> {}", sampling.get(), mode);
        sampling.set(mode);
        samplingChosen = true;
        store(AppSetting.SAMPLING, mode);
    }

    // Each write waits for the one before it, so a quick second choice is never overwritten by the first.
    private <T> void store(final AppSetting<T> setting, final T value) {
        lastWrite =
                lastWrite.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(setting, value), background);
    }

    private <T> void write(final AppSetting<T> setting, final T value) {
        boolean failed;
        try {
            failed = settings.put(setting, value).isErr();
        } catch (RuntimeException e) {
            log.error("storing {} failed", setting.key(), e);
            failed = true;
        }
        if (failed) {
            log.warn("generation: {} could not be stored; it holds until the app closes", setting.key());
        }
        final boolean shown = failed;
        fx.execute(() -> saveFailed.set(shown));
    }

    /**
     * The thinking level.
     *
     * @return the property; the stored level once {@link #load} has arrived
     */
    public ObjectProperty<ReasoningSetting> reasoningProperty() {
        return reasoning;
    }

    /**
     * The sampling mode.
     *
     * @return the property; the stored mode once {@link #load} has arrived
     */
    public ObjectProperty<SamplingSetting> samplingProperty() {
        return sampling;
    }

    /**
     * What overrides the thinking level for this launch.
     *
     * @return the variable or property name; {@code null} when the stored choice decides
     */
    public ReadOnlyStringProperty reasoningOverrideProperty() {
        return reasoningOverride;
    }

    /**
     * What overrides the sampling mode for this launch.
     *
     * @return the variable or property name, or {@code null} when the stored choice decides
     */
    public ReadOnlyStringProperty samplingOverrideProperty() {
        return samplingOverride;
    }

    /**
     * Whether the stored choices have arrived.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty loadedProperty() {
        return loaded;
    }

    /**
     * Whether the last choice could not be stored.
     *
     * @return a read-only property; the choice still holds until the app closes
     */
    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed;
    }
}
