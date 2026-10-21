package ua.bookloom.persistence.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

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
    void backupTo_isNotAvailableYet() {
        final Result<Path> result = info.backupTo(dir.resolve("copy.db"));

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
    }
}
