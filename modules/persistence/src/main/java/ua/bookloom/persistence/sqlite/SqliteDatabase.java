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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * An open SQLite database in WAL mode: reads on their own connections, writes through one {@link WriteExecutor}.
 * Before the first release a file that does not fit is set aside and replaced (ADR-0043); there is no migration.
 */
@Slf4j
public final class SqliteDatabase implements AutoCloseable {

    static final int SCHEMA_VERSION = 1;

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    private static final int BUSY_TIMEOUT_MS = 5000;
    private static final String FX_THREAD = "JavaFX Application Thread";
    private static final String FAIL_ON_FX_PROPERTY = "bookloom.storage.failOnFxThread";
    private static final DateTimeFormatter ASIDE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> SIDECARS = List.of("-wal", "-shm");

    private final Path file;
    private final SQLiteDataSource dataSource;
    private final Jdbi jdbi;
    private final WriteExecutor writer;
    private final Set<String> warnedCallSites = ConcurrentHashMap.newKeySet();

    private SqliteDatabase(final Path file, final SQLiteDataSource dataSource) {
        this.file = file;
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
            if (!isUsable(file)) {
                setAside(file, clock);
            }
            final SQLiteDataSource source = newDataSource(file);
            applySchema(source);
            log.info("Database open file={} schemaVersion={}", file.getFileName(), SCHEMA_VERSION);
            return Result.ok(new SqliteDatabase(file, source));
        } catch (IOException | RuntimeException e) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Storage failure", "The database could not be opened.", "storage open", e);
            log.error("Database open failed code={}", error.code(), e);
            return Result.err(error);
        }
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
        try {
            return Result.ok(jdbi.withHandle(work::apply));
        } catch (RuntimeException e) {
            final AppError error = AppError.of(
                    ErrorCode.internal, "Storage failure", "The data could not be read.", "storage " + operation, e);
            log.error("Storage read failed operation={} code={}", operation, error.code(), e);
            return Result.err(error);
        }
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
        return writer.execute(operation, work);
    }

    @Override
    public void close() {
        log.info("Database closing file={}", file.getFileName());
        writer.close();
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
                .walk(frames -> frames.filter(f -> f.getDeclaringClass() != SqliteDatabase.class)
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

    private static boolean isUsable(final Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            log.debug("Database file absent or empty, creating fresh");
            return true;
        }
        try (Connection c = newDataSource(file).getConnection();
                Statement s = c.createStatement()) {
            final String check = firstString(s, "pragma quick_check");
            final int version = Integer.parseInt(firstString(s, "pragma user_version"));
            log.debug("Database checked quickCheck={} userVersion={}", check, version);
            return "ok".equals(check) && version == SCHEMA_VERSION;
        } catch (SQLException e) {
            log.debug("Database cannot be opened: {}", e.getMessage());
            return false;
        }
    }

    private static String firstString(final Statement s, final String sql) throws SQLException {
        try (var rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static void setAside(final Path file, final Clock clock) throws IOException {
        final String suffix = ".old-" + ASIDE_STAMP.format(LocalDateTime.now(clock));
        final Path aside = file.resolveSibling(file.getFileName() + suffix);
        log.warn("Database does not fit, set aside file={} aside={}", file.getFileName(), aside.getFileName());
        Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
        for (final String sidecar : SIDECARS) {
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + sidecar));
        }
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
        Jdbi.create(source).useHandle(h -> h.createScript(script).execute());
    }
}
