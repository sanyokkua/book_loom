package ua.bookloom.api.persistence;

import java.nio.file.Path;
import java.util.Optional;
import ua.bookloom.api.Result;

/** Where the stored work lives and how to copy it, for Settings › Storage &amp; logs. */
public interface StorageInfo {

    /**
     * Names the file the stored work is kept in.
     *
     * @return the file, or empty when the work is kept in memory only
     */
    Optional<Path> location();

    /**
     * Names the file this launch moved the stored work to because it could not be read by this version, so the
     * person is told once that they start with empty storage and where the old work is.
     *
     * @return the file the old work was moved to, or empty when nothing was moved (always empty in memory)
     */
    default Optional<Path> setAsideAtOpen() {
        return Optional.empty();
    }

    /**
     * Measures the stored work.
     *
     * @return the size of the stored file in bytes; zero when there is no file
     */
    long sizeBytes();

    /**
     * Writes a copy of the stored work and leaves the stored work unchanged.
     *
     * @param destination the non-null file to write; it must not already exist
     * @return the written file; {@code validation} when there is no file to copy, the destination exists or its
     *     folder is missing; {@code internal} when the copy itself fails
     */
    Result<Path> backupTo(Path destination);

    /**
     * Deletes the stored work — the database file with its write-ahead and shared-memory files, nothing else in its
     * folder — for Delete all data. Once it succeeds the storage refuses every read and write until the application
     * starts again, so nothing creates a new file under the old name before then.
     *
     * @return {@code true} when the files are gone; {@code validation} when the work is kept in memory only;
     *     {@code internal} when a file could not be deleted, and then the stored work is whole and still usable
     */
    Result<Boolean> deleteAll();
}
