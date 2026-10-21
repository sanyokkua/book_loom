package ua.bookloom.llm.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/**
 * Reads a model's stored settings through the repository on every request, so a save by any writer is seen by the next
 * one. The last settings seen are kept only to name a change in the log and to stand in when the store cannot be read.
 */
@Slf4j
@Singleton
public final class ModelSettingsCache {

    private final ModelSettingsRepository repository;
    private final Map<String, ModelSettings> lastSeen = new ConcurrentHashMap<>();

    @Inject
    public ModelSettingsCache(final ModelSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * The settings of one model.
     *
     * @param providerId the provider's id; non-null
     * @param modelId the model's id; non-null
     * @return the stored settings; the last ones seen when the store cannot be read; else the defaults
     */
    public ModelSettings current(final String providerId, final String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        final String key = providerId + '\u0000' + modelId;
        final ModelSettings fallback = lastSeen.getOrDefault(key, ModelSettings.defaults(providerId, modelId));
        final var found = repository.find(providerId, modelId);
        if (found.isErr()) {
            log.warn("Model settings unreadable providerId={} modelId={}; using the last seen", providerId, modelId);
            return fallback;
        }
        final ModelSettings settings =
                Objects.requireNonNull(found.data(), "found").orElse(ModelSettings.defaults(providerId, modelId));
        remember(key, settings);
        log.debug("Model settings read providerId={} modelId={}", providerId, modelId);
        return settings;
    }

    /**
     * Stores a model's settings.
     *
     * @param settings the settings to keep; non-null
     * @return the repository's answer
     */
    Result<ModelSettings> save(final ModelSettings settings) {
        Objects.requireNonNull(settings, "settings");
        final Result<ModelSettings> saved = repository.save(settings);
        log.debug(
                "Model settings save providerId={} modelId={} ok={}",
                settings.providerId(),
                settings.modelId(),
                saved.isOk());
        if (saved.isOk()) {
            remember(settings.providerId() + '\u0000' + settings.modelId(), settings);
        }
        return saved;
    }

    private void remember(final String key, final ModelSettings settings) {
        final ModelSettings before = lastSeen.put(key, settings);
        if (before != null && !before.equals(settings)) {
            log.info(
                    "Model settings changed providerId={} modelId={} temperatureChoice={} contextWindow={} maxOutput={} outputField={}",
                    settings.providerId(),
                    settings.modelId(),
                    settings.temperatureChoice(),
                    settings.contextWindow(),
                    settings.maxOutput(),
                    settings.outputField());
        }
    }
}
