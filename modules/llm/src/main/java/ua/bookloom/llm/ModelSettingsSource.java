package ua.bookloom.llm;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.llm.profile.ResolvedProfile;
import ua.bookloom.llm.provider.RefusalMemory;

/**
 * Where a model's settings come from at every call: the stored settings, the settings it refused, and the profile
 * resolved from them.
 *
 * @param settings the stored settings by provider id and model id, read afresh for every call
 * @param refusals where a refused setting is remembered; {@code null} when it is not
 * @param profiles resolves the model's profile from its stored settings
 */
record ModelSettingsSource(
        BiFunction<String, String, ModelSettings> settings,
        @Nullable RefusalMemory refusals,
        Function<ModelSettings, ResolvedProfile> profiles) {

    ModelSettingsSource {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(profiles, "profiles");
    }
}
