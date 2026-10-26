package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.TermType;

/** Proves the stored-file facts the Storage tab shows. */
class SqliteStorageInfoTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T10:15:30Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    private SqliteDatabase db;
    private SqliteStorageInfo info;

    @BeforeEach
    void openDatabase() {
        db = Objects.requireNonNull(
                SqliteDatabase.open(dir.resolve("bookloom.db"), CLOCK).data());
        info = new SqliteStorageInfo(db);
    }

    @AfterEach
    void closeDatabase() {
        db.close();
    }

    @Test
    void location_isTheDatabaseFile() {
        assertThat(info.location()).contains(dir.resolve("bookloom.db"));
    }

    @Test
    void setAsideAtOpen_unreadableFileReplaced_namesTheFileItWasMovedTo() throws Exception {
        db.close();
        Files.writeString(dir.resolve("bookloom.db"), "not a database ".repeat(40));
        db = Objects.requireNonNull(
                SqliteDatabase.open(dir.resolve("bookloom.db"), CLOCK).data());

        assertThat(new SqliteStorageInfo(db).setAsideAtOpen()).contains(dir.resolve("bookloom.db.old-20261020-101530"));
    }

    @Test
    void sizeBytes_isTheMainFilePlusTheWalFile() throws Exception {
        final Path main = dir.resolve("bookloom.db");
        final Path wal = dir.resolve("bookloom.db-wal");
        final long expected = Files.size(main) + (Files.exists(wal) ? Files.size(wal) : 0L);

        assertThat(info.sizeBytes()).isEqualTo(expected).isPositive();
    }

    @Test
    void sizeBytes_afterTheFileIsGone_isZero() throws Exception {
        db.close();
        Files.deleteIfExists(dir.resolve("bookloom.db"));
        Files.deleteIfExists(dir.resolve("bookloom.db-wal"));
        Files.deleteIfExists(dir.resolve("bookloom.db-shm"));

        assertThat(info.sizeBytes()).isZero();
        db = Objects.requireNonNull(
                SqliteDatabase.open(dir.resolve("bookloom.db"), CLOCK).data());
    }

    @Test
    void backupTo_threeProjectsStored_copyOpensWithThreeProjects() {
        storeProjects("a", "b", "c");
        final Path copy = dir.resolve("copy.db");

        final Result<Path> result = info.backupTo(copy);

        assertThat(result.data()).isEqualTo(copy);
        try (SqliteDatabase opened =
                Objects.requireNonNull(SqliteDatabase.open(copy, CLOCK).data())) {
            assertThat(new SqliteProjectRepository(opened, new JsonCodec())
                            .all()
                            .data())
                    .hasSize(3);
        }
        assertThat(dir.toFile().list()).noneMatch(name -> name.contains(".old-"));
    }

    @Test
    void backupTo_destinationExists_validationAndFileUntouched() throws Exception {
        final Path taken = dir.resolve("taken.db");
        Files.writeString(taken, "precious");

        final Result<Path> result = info.backupTo(taken);

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(taken)).isEqualTo("precious");
    }

    @Test
    void backupTo_folderMissing_validation() {
        final Result<Path> result = info.backupTo(dir.resolve("nowhere").resolve("copy.db"));

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
    }

    @Test
    void backupTo_afterCopy_storedWorkUnchangedAndWritable() {
        storeProjects("a", "b");
        info.backupTo(dir.resolve("copy.db"));

        final SqliteProjectRepository projects = new SqliteProjectRepository(db, new JsonCodec());
        assertThat(projects.all().data()).hasSize(2);
        assertThat(projects.save(project("z")).isOk()).isTrue();
        assertThat(projects.all().data()).hasSize(3);
    }

    // IF a failed copy left a half file behind, THEN the next attempt under that name would be refused as taken.
    @Test
    void backupTo_folderNotWritable_internalAndNoFileLeft() throws Exception {
        final Path locked = Files.createDirectory(dir.resolve("locked"));
        assertThat(locked.toFile().setWritable(false)).isTrue();
        try {
            assumeThat(Files.isWritable(locked))
                    .as("the folder really is read-only here")
                    .isFalse();

            final Result<Path> result = info.backupTo(locked.resolve("copy.db"));

            assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
            assertThat(locked.resolve("copy.db")).doesNotExist();
        } finally {
            assertThat(locked.toFile().setWritable(true)).isTrue();
        }
    }

    // IF the copy held the writer for good, THEN a save made while it runs would never come back.
    @Test
    void backupTo_whileWritesAreSubmitted_bothSucceed() throws Exception {
        storeProjects("a", "b");
        final SqliteProjectRepository projects = new SqliteProjectRepository(db, new JsonCodec());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            final Future<Result<Path>> copy = pool.submit(() -> info.backupTo(dir.resolve("busy.db")));
            final Future<Boolean> saved =
                    pool.submit(() -> projects.save(project("late")).isOk());

            assertThat(saved.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(copy.get(30, TimeUnit.SECONDS).isOk()).isTrue();
        }
        assertThat(projects.all().data()).hasSize(3);
    }

    // IF a removed project's pages stayed in the file, THEN the Storage tab would show 100 MB for no projects.
    @Test
    void sizeBytes_afterTheOnlyLargeProjectIsRemoved_shrinksWithIt() {
        storeProjects("a");
        final List<GlossaryEntry> entries = IntStream.range(0, 3000)
                .mapToObj(i -> new GlossaryEntry(
                        "a:" + i, "a", "Name" + i, "Імʼя".repeat(100), TermType.CHARACTER, Gender.UNKNOWN, false))
                .toList();
        assertThat(new SqliteGlossaryRepository(db, new JsonCodec())
                        .putAll("a", entries)
                        .isOk())
                .isTrue();
        final long stored = info.sizeBytes();

        assertThat(new SqliteProjectRepository(db, new JsonCodec()).delete("a").data())
                .isTrue();

        assertThat(info.sizeBytes()).isLessThan(stored / 4);
    }

    private void storeProjects(final String... ids) {
        final SqliteProjectRepository projects = new SqliteProjectRepository(db, new JsonCodec());
        for (final String id : ids) {
            assertThat(projects.save(project(id)).isOk()).isTrue();
        }
    }

    private static Project project(final String id) {
        return new Project(
                id,
                Path.of("hash-" + id + ".epub"),
                BookFormat.EPUB,
                "hash-" + id,
                BookBrief.defaults("en").withLanguages("en", "uk"),
                id,
                NameOrigin.USER,
                "Book",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
    }
}
