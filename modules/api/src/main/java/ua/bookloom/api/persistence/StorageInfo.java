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
     * Measures the stored work.
     *
     * @return the size of the stored file in bytes; zero when there is no file
     */
    long sizeBytes();

    /**
     * Writes a copy of the stored work and leaves the stored work unchanged.
     *
     * @param destination the non-null file to write; it must not already exist
     * @return the written file, {@code validation} when there is no file to copy or the destination exists, or a
     *     typed storage failure
     */
    Result<Path> backupTo(Path destination);
}
