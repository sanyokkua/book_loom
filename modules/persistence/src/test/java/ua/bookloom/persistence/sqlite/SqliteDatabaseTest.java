package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

class SqliteDatabaseTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:15:30Z"), ZoneOffset.UTC);
    private static final String GUARD = "bookloom.storage.failOnFxThread";

    @TempDir
    Path dir;

    private @Nullable String savedGuard;

    private SqliteDatabase open() {
        final Result<SqliteDatabase> opened = SqliteDatabase.open(dir.resolve("bookloom.db"), CLOCK);
        assertThat(opened.isOk()).isTrue();
        return Objects.requireNonNull(opened.data());
    }

    private static List<String> tables(final Handle h) {
        return h.createQuery("select name from sqlite_master where type = 'table' order by name")
                .mapTo(String.class)
                .list();
    }

    private static void insertProjectWithSegment(final Handle h) {
        h.execute(
                "insert into books(content_hash, source_path, format, title, json) values ('h', 'p', 'EPUB', 't', '{}')");
        h.execute("insert into projects(id, content_hash, name, name_key, name_origin, source_lang, target_lang,"
                + " created_at, last_opened_at, brief_json) values ('p1', 'h', 'N', 'n', 'USER', 'en', 'uk',"
                + " '2026-10-20T00:00:00Z', '2026-10-20T00:00:00Z', '{}')");
        h.execute("insert into segments(project_id, segment_id, ord, unit_id, status, json)"
                + " values ('p1', 's1', 0, 'u1', 'PENDING', '{}')");
    }

    @Test
    void exclusive_work_runsOnTheWriterThreadOutsideATransaction() {
        try (SqliteDatabase db = open()) {
            final Result<String> ran =
                    db.exclusive("probe", h -> Thread.currentThread().getName() + " inTx=" + h.isInTransaction());

            assertThat(ran.data()).isEqualTo("bookloom-sqlite-writer inTx=false");
        }
    }

    @Test
    void exclusive_insideWrite_refused() {
        try (SqliteDatabase db = open()) {
            final Result<Result<Integer>> outer = db.write("outer", h -> db.exclusive("inner", inner -> 1));

            final Result<Integer> inner = Objects.requireNonNull(outer.data());
            assertThat(inner.isErr()).isTrue();
            assertThat(Objects.requireNonNull(inner.error()).details()).isEqualTo("storage inner");
        }
    }

    @Test
    void exclusive_afterClose_refused() {
        final SqliteDatabase db = open();
        db.close();

        final Result<Integer> ran = db.exclusive("late", h -> 1);

        assertThat(ran.isErr()).isTrue();
        assertThat(Objects.requireNonNull(ran.error()).details()).isEqualTo("storage late");
    }

    @BeforeEach
    void rememberGuard() {
        savedGuard = System.getProperty(GUARD);
    }

    @AfterEach
    void restoreGuard() {
        Optional.ofNullable(savedGuard)
                .ifPresentOrElse(value -> System.setProperty(GUARD, value), () -> System.clearProperty(GUARD));
    }

    @ParameterizedTest
    @CsvSource({"foreign_keys, 1", "busy_timeout, 5000", "journal_mode, wal", "synchronous, 1"})
    void open_twoConnections_bothCarryThePragma(final String name, final String expected) throws SQLException {
        try (SqliteDatabase db = open();
                Connection first = db.dataSource().getConnection();
                Connection second = db.dataSource().getConnection()) {
            assertThat(pragma(first, name)).isEqualTo(expected);
            assertThat(pragma(second, name)).isEqualTo(expected);
        }
    }

    private static String pragma(final Connection c, final String name) throws SQLException {
        try (var s = c.createStatement();
                var rs = s.executeQuery("pragma " + name)) {
            rs.next();
            return rs.getString(1);
        }
    }

    // IF a background reader could still use a closed database, THEN it would open a connection and recreate the -wal
    // files in a folder the application's owner (or a test) is deleting.
    @Test
    void closed_readOrWriteAfterClose_isRefusedAndLeavesNoSidecarFiles() throws IOException {
        final SqliteDatabase db = open();
        db.close();
        Files.deleteIfExists(dir.resolve("bookloom.db-wal"));
        Files.deleteIfExists(dir.resolve("bookloom.db-shm"));

        final Result<List<String>> read = db.read("list tables", SqliteDatabaseTest::tables);
        final Result<Integer> write = db.write("noop", handle -> 1);

        assertThat(read.isErr()).isTrue();
        assertThat(write.isErr()).isTrue();
        assertThat(dir.resolve("bookloom.db-wal")).doesNotExist();
        assertThat(dir.resolve("bookloom.db-shm")).doesNotExist();
    }

    @Test
    void open_freshFile_createsEveryTable() {
        try (SqliteDatabase db = open()) {
            final List<String> names =
                    db.read("list tables", SqliteDatabaseTest::tables).data();
            assertThat(names)
                    .contains(
                            "settings",
                            "providers",
                            "model_settings",
                            "books",
                            "projects",
                            "project_docs",
                            "segments",
                            "tm_entries",
                            "deferrals",
                            "meta");
            assertThat(db.read(
                                    "version",
                                    h -> h.createQuery("pragma user_version")
                                            .mapTo(Integer.class)
                                            .one())
                            .data())
                    .isEqualTo(2);
            assertThat(storedShapes(db)).isEqualTo(StoredShapes.current());
        }
    }

    private static String storedShapes(final SqliteDatabase db) {
        return Objects.requireNonNull(db.read(
                        "shapes",
                        h -> h.createQuery("select value from meta where key = 'record_shapes'")
                                .mapTo(String.class)
                                .one())
                .data());
    }

    @Test
    void open_storedRecordShapesChanged_renamesFileAsideAndCreatesFreshEmpty() {
        final String otherShapes = StoredShapes.fingerprint(List.of(OtherShape.class));
        try (SqliteDatabase first = open()) {
            first.write("mark", h -> {
                h.execute("insert into settings(key, value, type) values ('k', 'v', 'STRING')");
                return h.execute("update meta set value = ? where key = 'record_shapes'", otherShapes);
            });
        }
        try (SqliteDatabase second = open()) {
            assertThat(dir.resolve("bookloom.db.old-20261020-101530")).exists();
            assertThat(storedShapes(second)).isEqualTo(StoredShapes.current());
            assertThat(second.read(
                                    "count",
                                    h -> h.createQuery("select count(*) from settings")
                                            .mapTo(Integer.class)
                                            .one())
                            .data())
                    .isZero();
        }
    }

    private record OtherShape(String only) {}

    @Test
    void setAside_fileWithSidecars_movesTheSidecarsWithIt() throws IOException {
        Files.writeString(dir.resolve("bookloom.db"), "main bytes");
        Files.writeString(dir.resolve("bookloom.db-wal"), "wal bytes");
        Files.writeString(dir.resolve("bookloom.db-shm"), "shm bytes");

        SqliteDatabase.setAside(dir.resolve("bookloom.db"), CLOCK);

        assertThat(dir.resolve("bookloom.db.old-20261020-101530")).hasContent("main bytes");
        assertThat(dir.resolve("bookloom.db.old-20261020-101530-wal")).hasContent("wal bytes");
        assertThat(dir.resolve("bookloom.db.old-20261020-101530-shm")).hasContent("shm bytes");
        assertThat(dir.resolve("bookloom.db-wal")).doesNotExist();
        assertThat(dir.resolve("bookloom.db-shm")).doesNotExist();
    }

    @Test
    void open_otherSchemaVersion_renamesFileAsideAndCreatesFresh() throws IOException {
        try (SqliteDatabase first = open()) {
            first.write("mark", h -> {
                h.execute("pragma user_version = 99");
                return Boolean.TRUE;
            });
        }
        try (SqliteDatabase second = open()) {
            assertThat(dir.resolve("bookloom.db.old-20261020-101530")).exists();
            assertThat(second.read("tables", SqliteDatabaseTest::tables).data()).contains("segments");
        }
    }

    // IF a newer build's file were set aside without a word, THEN the person would find an empty BookLoom and no
    // reason.
    @Test
    void open_newerSchemaVersion_setsItAsideAndNamesTheFileItWasMovedTo() {
        try (SqliteDatabase first = open()) {
            first.write("mark", h -> h.execute("pragma user_version = " + (SqliteDatabase.SCHEMA_VERSION + 1)));
        }
        try (SqliteDatabase second = open()) {
            assertThat(second.setAside()).hasValue(dir.resolve("bookloom.db.old-20261020-101530"));
        }
    }

    @Test
    void open_fittingFile_setsNothingAside() {
        open().close();
        try (SqliteDatabase second = open()) {
            assertThat(second.setAside()).isEmpty();
        }
    }

    // IF a file that cannot be written were set aside, THEN a permission problem would cost the person every project.
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void open_readOnlyFile_failsWithAStorageErrorAndKeepsTheFile() throws IOException {
        open().close();
        final Path file = dir.resolve("bookloom.db");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));

        final Result<SqliteDatabase> opened = SqliteDatabase.open(file, CLOCK);

        assertThat(opened.isErr()).isTrue();
        assertThat(Objects.requireNonNull(opened.error()).message()).contains("cannot be written");
        assertThat(dir.resolve("bookloom.db.old-20261020-101530")).doesNotExist();
        assertThat(file).exists();
    }

    // IF a file another program holds locked were set aside, THEN the other program's open file would be moved away.
    @Test
    void open_fileLockedByAnotherConnection_failsAndKeepsTheFile() throws SQLException {
        open().close();
        final Path file = dir.resolve("bookloom.db");
        try (Connection holder = DriverManager.getConnection("jdbc:sqlite:" + file);
                var s = holder.createStatement()) {
            s.execute("pragma locking_mode = exclusive");
            s.execute("pragma journal_mode = delete");
            s.execute("begin exclusive");

            final Result<SqliteDatabase> opened = SqliteDatabase.open(file, CLOCK);

            assertThat(opened.isErr()).isTrue();
            assertThat(Objects.requireNonNull(opened.error()).message()).contains("in use");
            assertThat(dir.resolve("bookloom.db.old-20261020-101530")).doesNotExist();
        }
    }

    @Test
    void open_unreadableFile_renamesAside() throws IOException {
        Files.write(
                dir.resolve("bookloom.db"),
                "this is not a database, just garbage bytes".repeat(20).getBytes(StandardCharsets.UTF_8));
        try (SqliteDatabase db = open()) {
            assertThat(dir.resolve("bookloom.db.old-20261020-101530")).exists();
            assertThat(db.read("tables", SqliteDatabaseTest::tables).data()).contains("projects");
        }
    }

    @Test
    void deleteProject_cascadesToSegments() {
        try (SqliteDatabase db = open()) {
            db.write("seed", h -> {
                insertProjectWithSegment(h);
                return Boolean.TRUE;
            });
            db.write("delete", h -> h.execute("delete from projects where id = 'p1'"));
            final int left = Objects.requireNonNull(db.read(
                            "count",
                            h -> h.createQuery("select count(*) from segments")
                                    .mapTo(Integer.class)
                                    .one())
                    .data());
            assertThat(left).isZero();
        }
    }

    @Test
    void insertProject_sameNameKeyInOneBook_isRejectedAsInternalError() {
        try (SqliteDatabase db = open()) {
            db.write("seed", h -> {
                insertProjectWithSegment(h);
                return Boolean.TRUE;
            });
            final Result<Integer> dup = db.write(
                    "dup",
                    h -> h.execute(
                            "insert into projects(id, content_hash, name, name_key, name_origin, source_lang, target_lang,"
                                    + " created_at, last_opened_at, brief_json) values ('p2', 'h', 'n', 'n', 'USER', 'en',"
                                    + " 'uk', 'a', 'a', '{}')"));
            assertThat(dup.isErr()).isTrue();
            assertThat(Objects.requireNonNull(dup.error()).code()).isEqualTo(ErrorCode.internal);
            assertThat(Objects.requireNonNull(dup.error()).details()).isEqualTo("storage dup");
        }
    }

    @Test
    void write_onFxNamedThread_throwsWhenPropertySet() throws InterruptedException {
        System.setProperty(GUARD, "true");
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        try (SqliteDatabase db = open()) {
            final Thread fx = new Thread(
                    () -> {
                        try {
                            db.write("x", h -> Boolean.TRUE);
                        } catch (IllegalStateException e) {
                            thrown.set(e);
                        }
                    },
                    "JavaFX Application Thread");
            fx.start();
            fx.join();
        }
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void read_onFxNamedThread_throwsWhenPropertySet() throws InterruptedException {
        System.setProperty(GUARD, "true");
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        try (SqliteDatabase db = open()) {
            final Thread fx = new Thread(
                    () -> {
                        try {
                            db.read("x", h -> Boolean.TRUE);
                        } catch (IllegalStateException e) {
                            thrown.set(e);
                        }
                    },
                    "JavaFX Application Thread");
            fx.start();
            fx.join();
        }
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void read_onFxNamedThreadWithGuardOff_warnsAndStillReads() throws InterruptedException {
        final AtomicReference<Result<Boolean>> answer = new AtomicReference<>();
        System.setProperty(GUARD, "false");
        try (SqliteDatabase db = open()) {
            final Thread fx =
                    new Thread(() -> answer.set(db.read("x", h -> Boolean.TRUE)), "JavaFX Application Thread");
            fx.start();
            fx.join();
        }
        assertThat(answer.get()).isNotNull().extracting(Result::isOk).isEqualTo(true);
    }
}
