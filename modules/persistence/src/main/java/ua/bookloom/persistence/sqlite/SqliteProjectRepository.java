package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.Query;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;

/**
 * Stores a project in {@code projects} and its book once in {@code books}, keyed by the content hash: the book row
 * holds the one file location every project of that content opens from, written when the book is first stored and
 * changed only by {@link #relocateBook}, and goes with the book's last project ({@code design.md} D9, D10). A name is unique within one book by {@link Project#nameKey()}, folded in Java because
 * SQLite's {@code lower()} folds only ASCII.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteProjectRepository implements ProjectRepository {

    /** The absent language of an Auto source or an unchosen target, as the NOT NULL column holds it. */
    private static final String NO_LANGUAGE = "";

    /** The book row's document: no book text and no metadata beyond its columns today. */
    private static final String BOOK_DOCUMENT = "{}";

    private static final String SELECT = """
            SELECT p.id, p.content_hash, p.name, p.name_origin, p.created_at, p.last_opened_at, p.brief_json,
                   b.source_path, b.format, b.title
            FROM projects p JOIN books b ON b.content_hash = p.content_hash
            """;
    /** A stored book keeps its location: only {@link #relocateBook} moves it, so a stale project never moves it back. */
    private static final String INSERT_BOOK = """
            INSERT INTO books (content_hash, source_path, format, title, json)
            VALUES (:hash, :path, :format, :title, :json)
            ON CONFLICT (content_hash) DO NOTHING
            """;

    private static final String UPSERT_PROJECT = """
            INSERT INTO projects (id, content_hash, name, name_key, name_origin, source_lang, target_lang,
                                  created_at, last_opened_at, brief_json)
            VALUES (:id, :hash, :name, :nameKey, :nameOrigin, :source, :target, :createdAt, :lastOpenedAt, :brief)
            ON CONFLICT (id) DO UPDATE SET content_hash = excluded.content_hash, name = excluded.name,
                name_key = excluded.name_key, name_origin = excluded.name_origin, source_lang = excluded.source_lang,
                target_lang = excluded.target_lang, created_at = excluded.created_at,
                last_opened_at = excluded.last_opened_at, brief_json = excluded.brief_json
            """;
    private static final String NAME_CLASH =
            "SELECT count(*) FROM projects WHERE content_hash = :hash AND name_key = :nameKey AND id <> :id";
    private static final String DELETE_ORPHAN_BOOKS =
            "DELETE FROM books WHERE content_hash NOT IN (SELECT content_hash FROM projects)";

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<Project> save(final Project project) {
        Objects.requireNonNull(project, "project");
        log.debug("Project save projectId={} hash={}", project.id(), project.contentHash());
        return db.write("project save", h -> saveIn(h, project)).flatMap(Function.identity());
    }

    private Result<Project> saveIn(final Handle h, final Project project) {
        final int clashes = h.createQuery(NAME_CLASH)
                .bind("hash", project.contentHash())
                .bind("nameKey", project.nameKey())
                .bind("id", project.id())
                .mapTo(Integer.class)
                .one();
        if (clashes > 0) {
            log.debug("Project save refused, name in use projectId={} hash={}", project.id(), project.contentHash());
            return Result.err(AppError.of(
                    ErrorCode.validation, "Name in use", "Another project of this book already has that name."));
        }
        insertBookIfAbsent(h, project);
        upsertProject(h, project);
        final int orphans = h.execute(DELETE_ORPHAN_BOOKS);
        log.debug("Project saved projectId={} orphanBooksRemoved={}", project.id(), orphans);
        return Result.ok(project);
    }

    private static void insertBookIfAbsent(final Handle h, final Project project) {
        final int inserted = h.createUpdate(INSERT_BOOK)
                .bind("hash", project.contentHash())
                .bind("path", project.source().toString())
                .bind("format", project.format().name())
                .bind("title", project.title())
                .bind("json", BOOK_DOCUMENT)
                .execute();
        log.debug("Book row hash={} inserted={}", project.contentHash(), inserted > 0);
    }

    private void upsertProject(final Handle h, final Project project) {
        h.createUpdate(UPSERT_PROJECT)
                .bind("id", project.id())
                .bind("hash", project.contentHash())
                .bind("name", project.name())
                .bind("nameKey", project.nameKey())
                .bind("nameOrigin", project.nameOrigin().name())
                .bind("source", language(project.brief().sourceLanguage()))
                .bind("target", language(project.brief().targetLanguage()))
                .bind("createdAt", project.createdAt().toString())
                .bind("lastOpenedAt", project.lastOpenedAt().toString())
                .bind("brief", codec.write(project.brief()))
                .execute();
    }

    @Override
    public Result<Optional<Project>> find(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Project find projectId={}", projectId);
        return db.read(
                "project find",
                h -> projects(h, "WHERE p.id = :id")
                        .bind("id", projectId)
                        .map((rs, ctx) -> toProject(rs))
                        .findOne());
    }

    @Override
    public Result<Boolean> delete(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return db.write("project delete", h -> {
                    final boolean removed = h.createUpdate("DELETE FROM projects WHERE id = :id")
                                    .bind("id", projectId)
                                    .execute()
                            > 0;
                    final int orphans = h.execute(DELETE_ORPHAN_BOOKS);
                    log.debug("Project delete projectId={} removed={} booksRemoved={}", projectId, removed, orphans);
                    return removed;
                })
                .map(removed -> {
                    if (removed) {
                        compact(projectId);
                    }
                    return removed;
                });
    }

    // A removed project's pages stay in the file as free space, so a removed book of 100 MB kept the file at 100 MB.
    // The file is rewritten without them; a refused rewrite (another connection reading) only leaves the space.
    private void compact(final String projectId) {
        final Result<Boolean> compacted = db.exclusive("vacuum after project delete", h -> {
            h.execute("VACUUM");
            h.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            return true;
        });
        if (compacted.isErr()) {
            log.warn(
                    "Project delete projectId={}: the file keeps the freed space: {}",
                    projectId,
                    Objects.requireNonNull(compacted.error(), "error").code());
        } else {
            log.debug("Project delete projectId={}: the file was compacted", projectId);
        }
    }

    @Override
    public Result<List<Project>> all() {
        return db.read("project all", h -> {
            final List<Project> projects =
                    projects(h, "").map((rs, ctx) -> toProject(rs)).list();
            log.debug("Project all count={}", projects.size());
            return List.copyOf(projects);
        });
    }

    @Override
    public Result<Optional<Project>> findByPair(
            final String contentHash, @Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
        Objects.requireNonNull(contentHash, "contentHash");
        log.debug("Project findByPair hash={} source={} target={}", contentHash, sourceLanguage, targetLanguage);
        return db.read(
                "project findByPair",
                h -> projects(h, "WHERE p.content_hash = :hash AND p.source_lang = :source AND p.target_lang = :target")
                        .bind("hash", contentHash)
                        .bind("source", language(sourceLanguage))
                        .bind("target", language(targetLanguage))
                        .map((rs, ctx) -> toProject(rs))
                        .findFirst());
    }

    @Override
    public Result<Optional<Project>> findByNameKey(final String contentHash, final String nameKey) {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(nameKey, "nameKey");
        log.debug("Project findByNameKey hash={} nameKey={}", contentHash, nameKey);
        return db.read(
                "project findByNameKey",
                h -> projects(h, "WHERE p.content_hash = :hash AND p.name_key = :nameKey")
                        .bind("hash", contentHash)
                        .bind("nameKey", nameKey)
                        .map((rs, ctx) -> toProject(rs))
                        .findFirst());
    }

    @Override
    public Result<List<Project>> relocateBook(final String contentHash, final Path newPath) {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(newPath, "newPath");
        return db.write("project relocateBook", h -> {
            final int books = h.createUpdate("UPDATE books SET source_path = :path WHERE content_hash = :hash")
                    .bind("path", newPath.toString())
                    .bind("hash", contentHash)
                    .execute();
            final List<Project> moved = List.copyOf(projects(h, "WHERE p.content_hash = :hash")
                    .bind("hash", contentHash)
                    .map((rs, ctx) -> toProject(rs))
                    .list());
            log.debug("Book relocated hash={} books={} projects={} path={}", contentHash, books, moved.size(), newPath);
            return moved;
        });
    }

    @Override
    public Result<ExportRecord> recordExport(final String projectId, final ExportRecord record) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(record, "record");
        log.debug("Export recorded projectId={} written={}", projectId, record.written());
        return db.write("project recordExport", h -> {
            ProjectDocs.write(h, codec, projectId, DocKind.LAST_EXPORT, record);
            return record;
        });
    }

    @Override
    public Result<Optional<ExportRecord>> lastExport(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Last export projectId={}", projectId);
        return db.read(
                "project lastExport",
                h -> ProjectDocs.read(h, codec, projectId, DocKind.LAST_EXPORT, ExportRecord.class));
    }

    private static Query projects(final Handle h, final String where) {
        return h.createQuery(SELECT + where);
    }

    private Project toProject(final ResultSet rs) throws SQLException {
        return new Project(
                rs.getString("id"),
                Path.of(rs.getString("source_path")),
                BookFormat.valueOf(rs.getString("format")),
                rs.getString("content_hash"),
                codec.read(rs.getString("brief_json"), BookBrief.class),
                rs.getString("name"),
                NameOrigin.valueOf(rs.getString("name_origin")),
                rs.getString("title"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("last_opened_at")));
    }

    private static String language(@Nullable final String tag) {
        return tag == null ? NO_LANGUAGE : tag;
    }
}
