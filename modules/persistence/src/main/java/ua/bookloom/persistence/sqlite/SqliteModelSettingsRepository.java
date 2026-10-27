package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/** Keeps one {@link ModelSettings} document per provider and model, so refusals and measured speed survive a restart. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteModelSettingsRepository implements ModelSettingsRepository {

    private static final String UPSERT = """
            INSERT INTO model_settings (provider_id, model_id, json) VALUES (:provider, :model, :json)
            ON CONFLICT (provider_id, model_id) DO UPDATE SET json = excluded.json
            """;

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        return db.read("modelSettings find", h -> {
            final Optional<ModelSettings> found = h.createQuery(
                            "SELECT json FROM model_settings WHERE provider_id = :provider AND model_id = :model")
                    .bind("provider", providerId)
                    .bind("model", modelId)
                    .mapTo(String.class)
                    .findOne()
                    .map(json -> codec.read(json, ModelSettings.class));
            log.debug("Model settings read provider={} model={} present={}", providerId, modelId, found.isPresent());
            return found;
        });
    }

    @Override
    public Result<ModelSettings> save(final ModelSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return db.write("modelSettings save", h -> {
            final String json = codec.write(settings);
            log.trace("Model settings document {}", json);
            h.createUpdate(UPSERT)
                    .bind("provider", settings.providerId())
                    .bind("model", settings.modelId())
                    .bind("json", json)
                    .execute();
            log.debug(
                    "Model settings stored provider={} model={} refused={}",
                    settings.providerId(),
                    settings.modelId(),
                    settings.refused().size());
            return settings;
        });
    }

    @Override
    public Result<ModelSettings> update(
            final String providerId, final String modelId, final UnaryOperator<ModelSettings> change) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(change, "change");
        return db.write("modelSettings update", h -> {
            final ModelSettings current = h.createQuery(
                            "SELECT json FROM model_settings WHERE provider_id = :provider AND model_id = :model")
                    .bind("provider", providerId)
                    .bind("model", modelId)
                    .mapTo(String.class)
                    .findOne()
                    .map(json -> codec.read(json, ModelSettings.class))
                    .orElseGet(() -> ModelSettings.defaults(providerId, modelId));
            final ModelSettings changed = change.apply(current);
            h.createUpdate(UPSERT)
                    .bind("provider", providerId)
                    .bind("model", modelId)
                    .bind("json", codec.write(changed))
                    .execute();
            log.debug("Model settings updated provider={} model={}", providerId, modelId);
            return changed;
        });
    }

    @Override
    public Result<Boolean> delete(final String providerId, final String modelId) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
        return db.write("modelSettings delete", h -> {
            final int rows = h.createUpdate(
                            "DELETE FROM model_settings WHERE provider_id = :provider AND model_id = :model")
                    .bind("provider", providerId)
                    .bind("model", modelId)
                    .execute();
            log.debug("Model settings deleted provider={} model={} existed={}", providerId, modelId, rows > 0);
            return rows > 0;
        });
    }

    @Override
    public Result<List<ModelSettings>> all() {
        return db.read("modelSettings all", h -> {
            final List<ModelSettings> all = h.createQuery(
                            "SELECT json FROM model_settings ORDER BY provider_id, model_id")
                    .mapTo(String.class)
                    .map(json -> codec.read(json, ModelSettings.class))
                    .list();
            log.debug("Model settings listed count={}", all.size());
            return all;
        });
    }
}
