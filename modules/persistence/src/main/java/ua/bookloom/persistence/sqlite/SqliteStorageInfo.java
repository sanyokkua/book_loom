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

/** Names, measures and copies the database file for Settings › Storage &amp; logs. */
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
        log.debug("Backup requested destination={}", destination.getFileName());
        final Optional<AppError> refusal = refusal(destination);
        if (refusal.isPresent()) {
            return Result.err(refusal.get());
        }
        final Result<Integer> copied = db.exclusive("backup", h -> {
            h.createUpdate("VACUUM INTO ?").bind(0, destination.toString()).execute();
            return 0;
        });
        if (copied.isErr()) {
            log.debug("Backup failed destination={}", destination.getFileName());
            removePartial(destination);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Backup failed",
                    "The copy could not be written. Check that the folder can be written to and has room.",
                    "storage backup",
                    Optional.ofNullable(copied.error()).map(AppError::cause).orElse(null)));
        }
        log.info("Backup written file={}", destination.getFileName());
        return Result.ok(destination);
    }

    // The destination did not exist before the copy (it would have been refused), so whatever is there is a leftover.
    private static void removePartial(final Path destination) {
        try {
            if (Files.deleteIfExists(destination)) {
                log.debug("Backup leftover removed file={}", destination.getFileName());
            }
        } catch (IOException e) {
            log.debug("Backup leftover {} could not be removed: {}", destination.getFileName(), e.getMessage());
        }
    }

    private static Optional<AppError> refusal(final Path destination) {
        if (Files.exists(destination)) {
            log.debug("Backup refused, destination exists file={}", destination.getFileName());
            return Optional.of(AppError.of(
                    ErrorCode.validation,
                    "Backup not written",
                    "A file named " + destination.getFileName()
                            + " is already there. BookLoom never overwrites a file; choose a new name."));
        }
        final Path folder = destination.toAbsolutePath().getParent();
        if (folder == null || !Files.isDirectory(folder)) {
            log.debug("Backup refused, folder missing file={}", destination.getFileName());
            return Optional.of(AppError.of(
                    ErrorCode.validation,
                    "Backup not written",
                    "The folder for the copy does not exist. Choose a folder that does."));
        }
        return Optional.empty();
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
