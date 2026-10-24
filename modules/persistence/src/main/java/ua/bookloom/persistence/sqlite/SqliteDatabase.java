package ua.bookloom.persistence.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * An open SQLite database in WAL mode: reads on their own connections, writes through one {@link WriteExecutor}.
 * Before the first release a file that does not fit is set aside and replaced (ADR-0043); there is no migration. A
 * file fits when it passes {@code quick_check}, carries {@link #SCHEMA_VERSION} and was written for the stored record
 * shapes of this build ({@link StoredShapes}), since a document of another shape could not be read back.
 */
@Slf4j
public final class SqliteDatabase implements AutoCloseable {

    static final int SCHEMA_VERSION = 2;

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    private static final int BUSY_TIMEOUT_MS = 5000;
    private static final String FX_THREAD = "JavaFX Application Thread";
    private static final String FAIL_ON_FX_PROPERTY = "bookloom.storage.failOnFxThread";
    private static final DateTimeFormatter ASIDE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> SIDECARS = List.of("-wal", "-shm");
    private static final String SHAPES_KEY = "record_shapes";
    // SQLite's primary result codes (the low byte of the extended code): a file that fails with one of these is no
    // BookLoom database of this build and is set aside; any other failure (busy, locked, read-only, permission, I/O)
    // says nothing about the file's content and must never cost the person their projects.
    private static final int PRIMARY_CODE_MASK = 0xff;
    private static final int SQLITE_ERROR = 1;
    private static final int SQLITE_BUSY = 5;
    private static final int SQLITE_LOCKED = 6;
    private static final int SQLITE_CORRUPT = 11;
    private static final int SQLITE_NOTADB = 26;

    private final Path file;
    private final @Nullable Path setAside;
    private final SQLiteDataSource dataSource;
    private final Jdbi jdbi;
    private final WriteExecutor writer;
    private final Set<String> warnedCallSites = ConcurrentHashMap.newKeySet();
    // Every read and write holds the read side while it runs; close takes the write side, so it waits for them and
    // nothing starts after it, which could otherwise open a connection and recreate the -wal files of a closed file.
    private final ReentrantReadWriteLock use = new ReentrantReadWriteLock();
    private boolean closed;

    private SqliteDatabase(final Path file, final @Nullable Path setAside, final SQLiteDataSource dataSource) {
        this.file = file;
        this.setAside = setAside;
        this.dataSource = dataSource;
        this.jdbi = Jdbi.create(dataSource);
        this.writer = new WriteExecutor(jdbi);
    }

    /**
     * Opens the database at {@code file}, creating it when absent.
     *
     * @param file the database file; its folder must exist
     * @param clock stamps the name of a file set aside
     * @return the database, or an {@code internal} error when even a fresh file cannot be created
     */
    public static Result<SqliteDatabase> open(final Path file, final Clock clock) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(clock, "clock");
        log.debug("Opening database file={}", file.getFileName());
        try {
            final Path aside = isUsable(file) ? null : setAside(file, clock);
            final SQLiteDataSource source = newDataSource(file);
            applySchema(source);
            log.info(
                    "Database open file={} schemaVersion={} recordShapes={} setAside={}",
                    file.getFileName(),
                    SCHEMA_VERSION,
                    StoredShapes.current(),
                    aside == null ? null : aside.getFileName());
            return Result.ok(new SqliteDatabase(file, aside, source));
        } catch (SQLException | IOException | RuntimeException e) {
            final AppError error = openFailure(e);
            log.error("Database open failed code={} title={}", error.code(), error.title(), e);
            return Result.err(error);
        }
    }

    // What the person is told when the file cannot be opened; the cause stays for the log.
    private static AppError openFailure(final Throwable e) {
        final int code = sqliteCode(e);
        log.debug("Database open failure classified sqliteCode={}", code);
        if (code == SQLITE_BUSY || code == SQLITE_LOCKED) {
            return AppError.of(
                    ErrorCode.internal,
                    "Storage in use",
                    "The database file is in use by another program. Close it and start BookLoom again.",
                    "storage open locked",
                    e);
        }
        if (code > 0 || e instanceof IOException) {
            return AppError.of(
                    ErrorCode.internal,
                    "Storage not writable",
                    "The database file cannot be written. Check the permissions of the data folder and start"
                            + " BookLoom again.",
                    "storage open sqlite " + code,
                    e);
        }
        return AppError.of(
                ErrorCode.internal, "Storage failure", "The database could not be opened.", "storage open", e);
    }

    // The primary SQLite result code of the first SQLException in the chain, or -1 when there is none.
    private static int sqliteCode(final Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() > 0) {
                return sql.getErrorCode() & PRIMARY_CODE_MASK;
            }
        }
        return -1;
    }

    /**
     * The file this launch moved the stored work to because it did not fit this build, so the person can be told once.
     *
     * @return the set-aside file, or empty when the stored file fitted or none existed
     */
    public Optional<Path> setAside() {
        return Optional.ofNullable(setAside);
    }

    /** The database file, for the storage facts of Settings. */
    Path file() {
        return file;
    }

    /** The connection source, every connection of which carries the pragmas. */
    DataSource dataSource() {
        return dataSource;
    }

    /**
     * Runs a read on its own connection.
     *
     * @param operation a short name, used as the {@code storage <operation>} detail of a failure
     * @param work the read
     * @return the value, or an {@code internal} error
     * @throws IllegalStateException on the JavaFX thread when {@code bookloom.storage.failOnFxThread} is true
     */
    public <T> Result<T> read(final String operation, final Function<Handle, T> work) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(work, "work");
        checkThread(operation);
        log.debug("Read operation={}", operation);
        use.readLock().lock();
        try {
            if (closed) {
                return refusedAfterClose(operation);
            }
            return Result.ok(jdbi.withHandle(work::apply));
        } catch (RuntimeException e) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Storage failure", "The data could not be read.", "storage " + operation, e);
            log.error("Storage read failed operation={} code={}", operation, error.code(), e);
            return Result.err(error);
        } finally {
            use.readLock().unlock();
        }
    }

    private static <T> Result<T> refusedAfterClose(final String operation) {
        log.debug("Storage {} refused: the database is closed", operation);
        return Result.err(AppError.of(
                ErrorCode.internal,
                "Storage closed",
                "The data could not be reached because the application is closing.",
                "storage " + operation,
                null));
    }

    /**
     * Runs a write as one transaction on the single writer; the caller waits for it.
     *
     * @param operation a short name, used as the {@code storage <operation>} detail of a failure
     * @param work the write; a throw rolls the whole transaction back
     * @return the value, or an {@code internal} error
     * @throws IllegalStateException on the JavaFX thread when {@code bookloom.storage.failOnFxThread} is true
     */
    public <T> Result<T> write(final String operation, final Function<Handle, T> work) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(work, "work");
        checkThread(operation);
        use.readLock().lock();
        try {
            return closed ? refusedAfterClose(operation) : writer.execute(operation, work);
        } finally {
            use.readLock().unlock();
        }
    }

    /**
     * Runs work on the single writer thread outside any transaction, as for {@code VACUUM INTO}, which SQLite
     * refuses inside one; asked from inside a write it is refused, like a nested write.
     */
    <T> Result<T> exclusive(final String operation, final Function<Handle, T> work) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(work, "work");
        checkThread(operation);
        use.readLock().lock();
        try {
            return closed ? refusedAfterClose(operation) : writer.executeOutsideTransaction(operation, work);
        } finally {
            use.readLock().unlock();
        }
    }

    @Override
    public void close() {
        log.info("Database closing file={}", file.getFileName());
        use.writeLock().lock();
        try {
            closed = true;
            writer.close();
        } finally {
            use.writeLock().unlock();
        }
    }

    private void checkThread(final String operation) {
        if (!FX_THREAD.equals(Thread.currentThread().getName())) {
            return;
        }
        if (Boolean.getBoolean(FAIL_ON_FX_PROPERTY)) {
            throw new IllegalStateException("Storage " + operation + " on the JavaFX Application Thread");
        }
        final String site = callSite();
        if (warnedCallSites.add(site)) {
            log.warn("Storage {} on the JavaFX Application Thread at {}", operation, site, new Throwable("call site"));
        }
    }

    private static String callSite() {
        return StackWalker.getInstance()
                .walk(frames -> frames.filter(
                                f -> !SqliteDatabase.class.getName().equals(f.getClassName()))
                        .findFirst()
                        .map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                        .orElse("unknown"));
    }

    private static SQLiteDataSource newDataSource(final Path file) {
        final SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.enforceForeignKeys(true);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        final SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + file.toAbsolutePath());
        return source;
    }

    // False when the file is no database of this build (corrupt, not a database, another schema or record shapes);
    // a failure that says nothing about its content (busy, locked, read-only, permission, I/O) is thrown instead.
    private static boolean isUsable(final Path file) throws IOException, SQLException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            log.debug("Database file absent or empty, creating fresh");
            return true;
        }
        try (Connection c = newDataSource(file).getConnection();
                Statement s = c.createStatement()) {
            final String check = firstString(s, "pragma quick_check");
            final int version = Integer.parseInt(firstString(s, "pragma user_version"));
            log.debug("Database checked quickCheck={} userVersion={}", check, version);
            if (!"ok".equals(check) || version != SCHEMA_VERSION) {
                return false;
            }
            final String shapes = firstString(s, "select value from meta where key = '" + SHAPES_KEY + "'");
            log.debug("Database record shapes stored={} expected={}", shapes, StoredShapes.current());
            return StoredShapes.current().equals(shapes);
        } catch (SQLException e) {
            final int code = e.getErrorCode() & PRIMARY_CODE_MASK;
            if (code == SQLITE_CORRUPT || code == SQLITE_NOTADB || code == SQLITE_ERROR) {
                log.debug("Database does not fit sqliteCode={}: {}", code, e.getMessage());
                return false;
            }
            log.warn("Database cannot be checked sqliteCode={}: the file is kept", code);
            throw e;
        }
    }

    private static String firstString(final Statement s, final String sql) throws SQLException {
        try (var rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    /** Renames the file, and the WAL and shared-memory files beside it, to the stamped {@code .old-} name. */
    static Path setAside(final Path file, final Clock clock) throws IOException {
        final String suffix = ".old-" + ASIDE_STAMP.format(LocalDateTime.now(clock));
        final Path aside = file.resolveSibling(file.getFileName() + suffix);
        log.warn("Database does not fit, set aside file={} aside={}", file.getFileName(), aside.getFileName());
        Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
        for (final String sidecar : SIDECARS) {
            final Path held = file.resolveSibling(file.getFileName() + sidecar);
            if (Files.exists(held)) {
                final Path moved = aside.resolveSibling(aside.getFileName() + sidecar);
                log.debug("Database sidecar set aside sidecar={} aside={}", held.getFileName(), moved.getFileName());
                Files.move(held, moved, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return aside;
    }

    private static void applySchema(final DataSource source) throws IOException {
        final String script;
        try (InputStream in = SqliteDatabase.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IOException("schema resource missing " + SCHEMA_RESOURCE);
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        log.debug("Applying schema chars={}", script.length());
        Jdbi.create(source).useTransaction(h -> {
            h.createScript(script).execute();
            h.createUpdate("INSERT INTO meta (key, value) VALUES (:key, :value)"
                            + " ON CONFLICT (key) DO UPDATE SET value = excluded.value")
                    .bind("key", SHAPES_KEY)
                    .bind("value", StoredShapes.current())
                    .execute();
        });
    }
}
