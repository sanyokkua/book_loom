package ua.bookloom.persistence.memory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.StorageInfo;

/** The storage info of the in-memory adapters: there is no file, so there is nothing to locate or copy. */
@Slf4j
public final class InMemoryStorageInfo implements StorageInfo {

    @Override
    public Optional<Path> location() {
        return Optional.empty();
    }

    @Override
    public long sizeBytes() {
        return 0L;
    }

    @Override
    public Result<Path> backupTo(final Path destination) {
        Objects.requireNonNull(destination, "destination");
        log.debug("Backup refused: in-memory storage has no file destination={}", destination);
        return Result.err(AppError.of(
                ErrorCode.validation,
                "Nothing to back up",
                "The stored work is kept in memory only, so there is no file to copy."));
    }

    @Override
    public Result<Boolean> deleteAll() {
        log.debug("Delete all refused: in-memory storage has no file");
        return Result.err(AppError.of(
                ErrorCode.validation,
                "Nothing to delete",
                "The stored work is kept in memory only, so there is no file to delete."));
    }
}
