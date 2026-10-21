package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.StorageInfo;

/** Names and measures the database file for Settings; the backup arrives with task 7.4. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteStorageInfo implements StorageInfo {

    private final SqliteDatabase db;

    @Override
    public Optional<Path> location() {
        return Optional.of(db.file());
    }

    @Override
    public long sizeBytes() {
        final Path main = db.file();
        final long total = sizeOf(main) + sizeOf(main.resolveSibling(main.getFileName() + "-wal"));
        log.debug("Storage measured file={} bytes={}", main.getFileName(), total);
        return total;
    }

    @Override
    public Result<Path> backupTo(final Path destination) {
        Objects.requireNonNull(destination, "destination");
        log.debug("Backup requested before it is available destination={}", destination.getFileName());
        return Result.err(AppError.of(
                ErrorCode.internal, "Backup not available", "Saving a copy of the stored work is not available yet."));
    }

    private static long sizeOf(final Path file) {
        try {
            return Files.exists(file) ? Files.size(file) : 0L;
        } catch (IOException e) {
            log.debug("Size of {} unreadable: {}", file.getFileName(), e.getMessage());
            return 0L;
        }
    }
}
