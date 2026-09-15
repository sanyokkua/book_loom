package ua.bookloom.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

/** Internal filesystem seam for deterministic publication-failure tests. */
@FunctionalInterface
interface ExportMoveOperation {

    Path move(Path source, Path destination, CopyOption... options);

    static ExportMoveOperation nio() {
        return ExportMoveOperation::moveFile;
    }

    private static Path moveFile(final Path source, final Path destination, final CopyOption... options) {
        try {
            return Files.move(source, destination, options);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
