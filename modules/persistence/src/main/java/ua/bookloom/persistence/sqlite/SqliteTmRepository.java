package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.TmEntry;

/**
 * Stores a project's translation memory in {@code tm_entries}, one row per project, source hash and context key — a
 * repeated {@link #put} for the same key replaces the prior entry.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteTmRepository implements TmRepository {

    private static final String UPSERT = """
            INSERT INTO tm_entries (project_id, source_hash, context_key, json)
            VALUES (:project, :sourceHash, :contextKey, :json)
            ON CONFLICT (project_id, source_hash, context_key) DO UPDATE SET json = excluded.json
            """;
    private static final String SELECT = "SELECT json FROM tm_entries WHERE project_id = :project ";

    /** SQLite's {@code length()} of text counts characters, that is code points, as the port asks. */
    private static final String IN_BAND =
            "AND length(json_extract(json, '$.sourceInner')) BETWEEN :min AND :max ORDER BY rowid";

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<TmEntry> put(final TmEntry entry) {
        Objects.requireNonNull(entry, "entry");
        log.debug(
                "TM put projectId={} entryId={} sourceHash={} contextKey={}",
                entry.projectId(),
                entry.id(),
                entry.sourceHash(),
                entry.contextKey());
        return db.write("tm put", h -> {
            upsert(h, codec, entry.projectId(), entry);
            return entry;
        });
    }

    @Override
    public Result<List<TmEntry>> exact(final String projectId, final String sourceHash) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceHash, "sourceHash");
        log.debug("TM exact projectId={} sourceHash={}", projectId, sourceHash);
        return db.read(
                "tm exact",
                h -> List.copyOf(h.createQuery(SELECT + "AND source_hash = :sourceHash ORDER BY rowid")
                        .bind("project", projectId)
                        .bind("sourceHash", sourceHash)
                        .mapTo(String.class)
                        .map(json -> codec.read(json, TmEntry.class))
                        .list()));
    }

    @Override
    public Result<Optional<TmEntry>> context(final String projectId, final String sourceHash, final String contextKey) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(contextKey, "contextKey");
        log.debug("TM context projectId={} sourceHash={} contextKey={}", projectId, sourceHash, contextKey);
        return db.read(
                "tm context",
                h -> h.createQuery(SELECT + "AND source_hash = :sourceHash AND context_key = :contextKey")
                        .bind("project", projectId)
                        .bind("sourceHash", sourceHash)
                        .bind("contextKey", contextKey)
                        .mapTo(String.class)
                        .findOne()
                        .map(json -> codec.read(json, TmEntry.class)));
    }

    @Override
    public Result<List<TmEntry>> candidates(final String projectId, final int minChars, final int maxChars) {
        Objects.requireNonNull(projectId, "projectId");
        return db.read("tm candidates", h -> {
            final List<TmEntry> found = List.copyOf(h.createQuery(SELECT + IN_BAND)
                    .bind("project", projectId)
                    .bind("min", minChars)
                    .bind("max", maxChars)
                    .mapTo(String.class)
                    .map(json -> codec.read(json, TmEntry.class))
                    .list());
            log.debug("TM candidates projectId={} min={} max={} found={}", projectId, minChars, maxChars, found.size());
            return found;
        });
    }

    /** Inserts or replaces one entry under a project; the checkpoint shares it. */
    static void upsert(final Handle h, final JsonCodec codec, final String projectId, final TmEntry entry) {
        h.createUpdate(UPSERT)
                .bind("project", projectId)
                .bind("sourceHash", entry.sourceHash())
                .bind("contextKey", entry.contextKey())
                .bind("json", codec.write(entry))
                .execute();
    }
}
