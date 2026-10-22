package ua.bookloom.ui;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/** A hand-written {@link ModelSettingsRepository} that keeps what a test stored per provider and model in memory. */
public final class ScriptedModelSettings implements ModelSettingsRepository {

    private final Map<String, ModelSettings> stored = new ConcurrentHashMap<>();

    /** Stores a measured speed, in seconds per segment of a Fast draft, for one provider and model. */
    public void measured(final String providerId, final String modelId, final double secondsPerSegment) {
        final ModelSettings base = ModelSettings.defaults(providerId, modelId);
        save(new ModelSettings(
                providerId,
                modelId,
                base.temperatureChoice(),
                null,
                null,
                null,
                base.outputField(),
                List.of(),
                secondsPerSegment));
    }

    @Override
    public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
        return Result.ok(Optional.ofNullable(stored.get(providerId + "/" + modelId)));
    }

    @Override
    public Result<ModelSettings> save(final ModelSettings settings) {
        stored.put(settings.providerId() + "/" + settings.modelId(), settings);
        return Result.ok(settings);
    }

    @Override
    public Result<Boolean> delete(final String providerId, final String modelId) {
        return Result.ok(stored.remove(providerId + "/" + modelId) != null);
    }

    @Override
    public Result<List<ModelSettings>> all() {
        return Result.ok(List.copyOf(stored.values()));
    }
}
