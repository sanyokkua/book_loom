package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;

/**
 * How a screen reaches the chosen model: the factory that creates it, the settings that name it and the way its context
 * length is detected.
 *
 * @param models creates the model a call goes to
 * @param settings the chosen provider and model
 * @param detection the context length, in tokens, the provider reports for a model; empty when it reports none
 */
public record ModelAccess(
        ChatModelFactory models, SettingsViewModel settings, Function<ModelSelection, Optional<Integer>> detection) {

    /** Rejects a missing part. */
    public ModelAccess {
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(detection, "detection");
    }

    /** The access the injector builds, detecting the window through the providers. */
    @Inject
    public ModelAccess(
            final ChatModelFactory models, final SettingsViewModel settings, final ModelCapabilities capabilities) {
        this(models, settings, capabilities::detectedTokens);
    }

    /** An access that detects nothing, so no provider is asked for a context length. */
    public ModelAccess(final ChatModelFactory models, final SettingsViewModel settings) {
        this(models, settings, _ -> Optional.empty());
    }
}
