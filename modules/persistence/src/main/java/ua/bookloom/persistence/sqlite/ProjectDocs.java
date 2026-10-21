package ua.bookloom.persistence.sqlite;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Handle;

/** Reads and writes one per-project JSON document of {@code project_docs} on the caller's handle. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProjectDocs {

    private static final String SELECT = "SELECT json FROM project_docs WHERE project_id = :project AND kind = :kind";
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
                .bind("project", projectId)
                .bind("kind", kind.key())
                .mapTo(String.class)
                .findOne()
                .map(json -> codec.read(json, type));
    }

    /** Inserts or replaces a document; the project must exist (foreign key), or the write fails. */
    static void write(
            final Handle handle,
            final JsonCodec codec,
            final String projectId,
            final DocKind kind,
            final Object value) {
        handle.createUpdate(UPSERT)
                .bind("project", projectId)
                .bind("kind", kind.key())
                .bind("json", codec.write(value))
                .execute();
    }
}
