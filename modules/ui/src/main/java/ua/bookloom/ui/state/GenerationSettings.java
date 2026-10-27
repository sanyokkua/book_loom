package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
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
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.GenerationOverrides;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;
import ua.bookloom.ui.SettingWriter;

/**
 * The Generation tab's two stored choices, thinking and sampling, and the variable or property that overrides each
 * for this launch. A choice is stored the moment it is made and reaches the segments not translated yet, because a
 * run reads the settings when it starts. FX Application Thread only.
 */
@Slf4j
@Singleton
public final class GenerationSettings {

    private final SettingWriter writer;
    private final GenerationOverrides overrides;
    private final ObjectProperty<ReasoningSetting> reasoning = new SimpleObjectProperty<>(ReasoningSetting.OFF);
    private final ObjectProperty<SamplingSetting> sampling =
            new SimpleObjectProperty<>(SamplingSetting.PROGRAM_DEFAULTS);
    private final StringProperty reasoningOverride = new SimpleStringProperty();
    private final StringProperty samplingOverride = new SimpleStringProperty();
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private boolean reasoningChosen;
    private boolean samplingChosen;

    /**
     * Creates the holder.
     *
     * @param writer reads and stores the choices off the FX thread
     * @param overrides names what overrides a choice for this launch
     */
    @Inject
    public GenerationSettings(final SettingWriter writer, final GenerationOverrides overrides) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
    }

    /** Reads both stored choices and the overrides in the background and publishes them on the FX thread. */
    public void load() {
        log.debug("loading the generation settings");
        writer.load(
                () -> new Stored(
                        writer.read(AppSetting.REASONING),
                        writer.read(AppSetting.SAMPLING),
                        overrides.reasoning().orElse(null),
                        overrides.sampling().orElse(null)),
                this::publish);
    }

    /** What the store and the launch hold, read off the FX thread and published together. */
    private record Stored(
            ReasoningSetting reasoning,
            SamplingSetting sampling,
            @Nullable String reasoningSource,
            @Nullable String samplingSource) {}

    // A choice made before the stored values arrived wins over them.
    private void publish(final Stored stored) {
        if (!reasoningChosen) {
            reasoning.set(stored.reasoning());
        }
        if (!samplingChosen) {
            sampling.set(stored.sampling());
        }
        reasoningOverride.set(stored.reasoningSource());
        samplingOverride.set(stored.samplingSource());
        loaded.set(true);
        log.debug(
                "generation loaded reasoning={} sampling={} overrides={}/{}",
                reasoning.get(),
                sampling.get(),
                stored.reasoningSource(),
                stored.samplingSource());
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
        writer.put(AppSetting.REASONING, level);
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
        writer.put(AppSetting.SAMPLING, mode);
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
        return writer.saveFailedProperty();
    }
}
