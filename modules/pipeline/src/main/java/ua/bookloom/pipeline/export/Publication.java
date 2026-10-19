package ua.bookloom.pipeline.export;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The one way a checked temporary file takes the place of the file it was written for — the book and each side file
 * alike: a plain move that refuses an existing file, or with permission an atomic replacement, falling back to a plain
 * replacement where the filesystem cannot move atomically.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Publication {

    /**
     * Moves {@code temporary} onto {@code destination}.
     *
     * @throws UncheckedIOException carrying the move's own failure, a {@code FileAlreadyExistsException} when the
     *     destination exists and {@code overwrite} is off
     */
    static void move(
            final ExportMoveOperation moves, final Path temporary, final Path destination, final boolean overwrite) {
        log.debug("Publishing {} to {} overwrite={}", temporary, destination, overwrite);
        if (!overwrite) {
            moves.move(temporary, destination);
            return;
        }
        try {
            moves.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (UncheckedIOException failure) {
            if (!(failure.getCause() instanceof AtomicMoveNotSupportedException)) {
                throw failure;
            }
            log.warn("Atomic move is unsupported for {}; falling back to replacement", destination);
            moves.move(temporary, destination, REPLACE_EXISTING);
        }
    }
}
