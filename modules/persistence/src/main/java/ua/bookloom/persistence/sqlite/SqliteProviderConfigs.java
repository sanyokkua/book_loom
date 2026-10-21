package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigRules;
import ua.bookloom.api.llm.ProviderConfigs;

/**
 * Keeps the registered providers as JSON documents in {@code providers}. It starts empty (the presets belong to the
 * in-memory adapter), and {@link ProviderConfigRules} refuses a literal key before anything is written, so a row holds
 * the name of an environment variable and never a secret. The port has no failure channel on a read, so a read that
 * fails is logged by the database and answers as nothing found.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteProviderConfigs implements ProviderConfigs {

    private static final String UPSERT = """
            INSERT INTO providers (id, json) VALUES (:id, :json)
            ON CONFLICT (id) DO UPDATE SET json = excluded.json
            """;

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<ProviderConfig> save(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        final ProviderConfigRules.Check check = ProviderConfigRules.check(config);
        if (!check.isValid()) {
            final AppError refusal = check.errors().get(0);
            log.warn("Provider save refused id={} code={}", config.id(), refusal.code());
            return Result.err(refusal);
        }
        check.warnings().forEach(warning -> log.debug("Provider id={} warning={}", config.id(), warning));
        return db.write("providers save", h -> {
            final String json = codec.write(config);
            log.trace("Provider document {}", json);
            h.createUpdate(UPSERT).bind("id", config.id()).bind("json", json).execute();
            log.debug("Provider configuration saved id={} kind={}", config.id(), config.kind());
            return config;
        });
    }

    @Override
    public Result<Boolean> delete(final String id) {
        Objects.requireNonNull(id, "id");
        return db.write("providers delete", h -> {
            final int rows = h.createUpdate("DELETE FROM providers WHERE id = :id")
                    .bind("id", id)
                    .execute();
            log.debug("Provider configuration delete id={} removed={}", id, rows > 0);
            return rows > 0;
        });
    }

    @Override
    public Optional<ProviderConfig> find(final String id) {
        Objects.requireNonNull(id, "id");
        final Result<Optional<ProviderConfig>> read = db.read(
                "providers find",
                h -> h.createQuery("SELECT json FROM providers WHERE id = :id")
                        .bind("id", id)
                        .mapTo(String.class)
                        .findOne()
                        .map(json -> codec.read(json, ProviderConfig.class)));
        final Optional<ProviderConfig> found = read.isOk() ? Objects.requireNonNull(read.data()) : Optional.empty();
        log.debug("Provider configuration lookup id={} found={}", id, found.isPresent());
        return found;
    }

    @Override
    public List<ProviderConfig> all() {
        final Result<List<ProviderConfig>> read = db.read(
                "providers all",
                h -> h.createQuery("SELECT json FROM providers ORDER BY id")
                        .mapTo(String.class)
                        .map(json -> codec.read(json, ProviderConfig.class))
                        .list());
        final List<ProviderConfig> all = read.isOk() ? Objects.requireNonNull(read.data()) : List.of();
        log.debug("Provider configuration list count={}", all.size());
        return all;
    }
}
