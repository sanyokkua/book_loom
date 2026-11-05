package ua.bookloom.persistence.sqlite;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Handle;

/** Reads and writes one per-project JSON document of {@code project_docs} on the caller's handle. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProjectDocs {

    private static final String PROJECT = "project";

    private static final String SELECT = "SELECT json FROM project_docs WHERE project_id = :project AND kind = :kind";
    private static final String SELECT_PREFIXED =
            "SELECT json FROM project_docs WHERE project_id = :project AND kind >= :from AND kind < :to ORDER BY kind";
    private static final String SELECT_PREFIXED_IN_WRITE_ORDER = "SELECT json FROM project_docs"
            + " WHERE project_id = :project AND kind >= :from AND kind < :to ORDER BY rowid";
    private static final char SUFFIX_SEPARATOR = ':';
    private static final String UPSERT = """
            INSERT INTO project_docs (project_id, kind, json) VALUES (:project, :kind, :json)
            ON CONFLICT (project_id, kind) DO UPDATE SET json = excluded.json
            """;

    /**
     * Reads a document.
     *
     * @return the stored value if the project has that document, or empty if not
     */
    static <T> Optional<T> read(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final Class<T> type) {
        return handle.createQuery(SELECT)
                .bind(PROJECT, projectId)
                .bind("kind", kind.key())
                .mapTo(String.class)
                .findOne()
                .map(json -> codec.read(json, type));
    }

    /**
     * Reads one document of a kind a project holds several of, the row named {@code <kind>:<suffix>}.
     *
     * @return the stored value if the project has that document, or empty if not
     */
    static <T> Optional<T> read(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final String suffix,
            final Class<T> type) {
        return handle.createQuery(SELECT)
                .bind(PROJECT, projectId)
                .bind("kind", keyOf(kind, suffix))
                .mapTo(String.class)
                .findOne()
                .map(json -> codec.read(json, type));
    }

    /**
     * Reads every document of a kind a project holds several of.
     *
     * @return never null; empty if the project has none, ordered by suffix
     */
    static <T> List<T> readAll(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final Class<T> type) {
        return prefixed(handle, codec, projectId, kind, type, SELECT_PREFIXED);
    }

    /**
     * Reads every document of a kind a project holds several of, in the order each row was first written: the upsert
     * updates a row in place, so a replaced document keeps its row id and its place.
     *
     * @return never null; empty if the project has none, oldest first
     */
    static <T> List<T> readAllInWriteOrder(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final Class<T> type) {
        return prefixed(handle, codec, projectId, kind, type, SELECT_PREFIXED_IN_WRITE_ORDER);
    }

    private static <T> List<T> prefixed(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final Class<T> type,
            final String sql) {
        // Every "<kind>:" row sorts at or after "<kind>:" and before "<kind>;", the next character after the colon.
        return handle.createQuery(sql)
                .bind(PROJECT, projectId)
                .bind("from", kind.key() + SUFFIX_SEPARATOR)
                .bind("to", kind.key() + (char) (SUFFIX_SEPARATOR + 1))
                .mapTo(String.class)
                .map(json -> codec.read(json, type))
                .list();
    }

    /** Inserts or replaces a document; the project must exist (foreign key), or the write fails. */
    static void write(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final Object value) {
        upsert(handle, codec, projectId, kind.key(), value);
    }

    /** Inserts or replaces the document {@code <kind>:<suffix>}; the project must exist, or the write fails. */
    static void write(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final String suffix,
            final Object value) {
        upsert(handle, codec, projectId, keyOf(kind, suffix), value);
    }

    private static void upsert(
            final Handle handle, final JsonCodec codec, final String projectId, final String kind, final Object value) {
        handle.createUpdate(UPSERT)
                .bind(PROJECT, projectId)
                .bind("kind", kind)
                .bind("json", codec.write(value))
                .execute();
    }

    private static String keyOf(final DocKind kind, final String suffix) {
        return kind.key() + SUFFIX_SEPARATOR + suffix;
    }
}
