package ua.bookloom.persistence.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/** The files a database is kept in on disk: the file itself and the WAL and shared-memory files beside it. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DatabaseFiles {

    /** The suffixes SQLite adds to the file's name for its WAL and shared-memory files. */
    static final List<String> SIDECARS = List.of("-wal", "-shm");

    /**
     * Deletes the WAL and shared-memory files, then the file: a WAL left beside a new file of the same name could be
     * replayed into it, so the file goes last and a failure part-way leaves it in place.
     *
     * @param file the database file; it must have no open connection
     */
    static void delete(final Path file) throws IOException {
        for (final String sidecar : SIDECARS) {
            final Path held = file.resolveSibling(file.getFileName() + sidecar);
            final boolean deleted = Files.deleteIfExists(held);
            log.debug("Database sidecar {} deleted={}", held.getFileName(), deleted);
        }
        Files.deleteIfExists(file);
    }

    /** {@return the failure of a delete that left the stored work in place} */
    static AppError notDeleted(final @Nullable Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Data not deleted",
                "The data folder could not be changed, so the stored work is kept.",
                "storage delete",
                cause);
    }
}
