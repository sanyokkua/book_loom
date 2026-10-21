package ua.bookloom.pipeline.prompt;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;

/**
 * Resolves the reasoning level and the sampling mode a run starts with: the environment variable or property when one
 * is set, else the stored Generation setting, else today's default.
 */
@Slf4j
@Singleton
public final class RequestDefaultsSource {

    private final Supplier<Map<String, String>> env;
    private final Supplier<@Nullable String> reasoningProperty;
    private final Supplier<@Nullable String> samplingProperty;
    private final @Nullable SettingsRepository settings;

    /**
     * Creates the source over the real environment and the stored settings.
     *
     * @param settings where the Generation settings are kept; never null
     */
    @Inject
    public RequestDefaultsSource(final SettingsRepository settings) {
        this(
                System::getenv,
                () -> System.getProperty(ReasoningPlan.PROPERTY_NAME),
                () -> System.getProperty("bookloom.sampling"),
                Objects.requireNonNull(settings, "settings"));
    }

    RequestDefaultsSource(
            final Supplier<Map<String, String>> env,
            final Supplier<@Nullable String> reasoningProperty,
            final Supplier<@Nullable String> samplingProperty,
            @Nullable final SettingsRepository settings) {
        this.env = Objects.requireNonNull(env, "env");
        this.reasoningProperty = Objects.requireNonNull(reasoningProperty, "reasoningProperty");
        this.samplingProperty = Objects.requireNonNull(samplingProperty, "samplingProperty");
        this.settings = settings;
    }

    /**
     * A source that consults the environment and properties only, for the command line, the eval and tests.
     *
     * @return a source with no stored settings behind it
     */
    public static RequestDefaultsSource environmentOnly() {
        return new RequestDefaultsSource(
                System::getenv,
                () -> System.getProperty(ReasoningPlan.PROPERTY_NAME),
                () -> System.getProperty("bookloom.sampling"),
                null);
    }

    /**
     * Reads the defaults now. Nothing is cached, so a change made in Settings reaches the next run.
     *
     * @return the defaults for the run about to start; the stored setting is used only where no environment variable
     *     or property names a value, and an unreadable or empty store gives Off and the server's own sampling
     */
    public RequestDefaults current() {
        final ReasoningPlan reasoning = reasoning();
        final SamplingMode sampling = sampling();
        log.debug(
                "Request defaults reasoning={} sampling={} stored={}",
                reasoning.token(),
                sampling.token(),
                settings != null);
        return new RequestDefaults(reasoning, sampling);
    }

    private ReasoningPlan reasoning() {
        final Map<String, String> environment = env.get();
        final String property = reasoningProperty.get();
        if (ReasoningPlan.isSet(environment, property)) {
            log.debug("Reasoning source=env");
            return ReasoningPlan.resolve(environment, property);
        }
        final ReasoningSetting stored = stored(AppSetting.REASONING);
        log.debug("Reasoning source=stored level={}", stored);
        return ReasoningPlan.stored(ReasoningLevel.of(stored));
    }

    private SamplingMode sampling() {
        final Optional<SamplingMode> explicit = SamplingMode.explicit(env.get(), samplingProperty.get());
        if (explicit.isPresent()) {
            log.debug("Sampling source=env mode={}", explicit.get().token());
            return explicit.get();
        }
        final SamplingSetting stored = stored(AppSetting.SAMPLING);
        log.debug("Sampling source=stored setting={}", stored);
        return stored == SamplingSetting.TUNED ? SamplingMode.TUNED : SamplingMode.SERVER;
    }

    private <T> T stored(final AppSetting<T> setting) {
        final T fallback = Objects.requireNonNull(setting.defaultValue(), "default");
        if (settings == null) {
            return fallback;
        }
        final Result<Optional<T>> read = settings.get(setting);
        if (read.isErr()) {
            log.warn("Could not read setting {}; using its default", setting.key());
            return fallback;
        }
        return Objects.requireNonNull(read.data(), "data").orElse(fallback);
    }
}
