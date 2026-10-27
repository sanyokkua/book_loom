package ua.bookloom.llm.testing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/** A hand-written in-memory {@link ModelSettingsRepository} for {@code :llm} tests. */
public final class FakeModelSettingsRepository implements ModelSettingsRepository {

    private final Map<String, ModelSettings> byModel = new ConcurrentHashMap<>();

    @Override
    public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
        return Result.ok(Optional.ofNullable(byModel.get(key(providerId, modelId))));
    }

    @Override
    public Result<ModelSettings> save(final ModelSettings settings) {
        byModel.put(key(settings.providerId(), settings.modelId()), settings);
        return Result.ok(settings);
    }

    @Override
    public Result<ModelSettings> update(
            final String providerId, final String modelId, final UnaryOperator<ModelSettings> change) {
        return Result.ok(byModel.compute(
                key(providerId, modelId),
                (key, stored) -> change.apply(stored == null ? ModelSettings.defaults(providerId, modelId) : stored)));
    }

    @Override
    public Result<Boolean> delete(final String providerId, final String modelId) {
        return Result.ok(byModel.remove(key(providerId, modelId)) != null);
    }

    @Override
    public Result<List<ModelSettings>> all() {
        return Result.ok(List.copyOf(byModel.values()));
    }

    private static String key(final String providerId, final String modelId) {
        return providerId + '\u0000' + modelId;
    }
}
