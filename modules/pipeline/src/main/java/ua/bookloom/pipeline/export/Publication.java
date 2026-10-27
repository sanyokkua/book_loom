package ua.bookloom.pipeline.export;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.util.files.FileFailures;
import ua.bookloom.util.files.FileWrites;

/**
 * The one way a checked temporary file takes the place of the file it was written for — the book and each side file
 * alike: a plain move that refuses an existing file, or with permission an atomic replacement, falling back to a plain
 * replacement where the filesystem cannot move atomically.
 *
 * <p>The temporary file is always a new one, created beside the target under a name nothing else uses: a hidden file
 * that already stands there — a person's own, or a link — is never written over and never deleted.
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

    /**
     * Creates a new, empty temporary file beside {@code target} under a name nothing else holds.
     *
     * @throws UncheckedIOException carrying the creation's own failure
     */
    static Path reserveTemporary(final Path target) {
        final Path created = FileWrites.reserveBeside(target);
        log.debug("Reserved temporary {} for {}", created, target);
        return created;
    }

    /**
     * The failure a person reads for a file that could not be written or put in place for a reason they can fix.
     *
     * @return the failure if the reason is a missing folder, a refused permission, a read-only place or a full disk,
     *     or empty if it is none of these
     */
    static Optional<AppError> fixableWriteError(final Path target, final Throwable failure) {
        final Optional<AppError> fixable = FileFailures.fixable(target, failure);
        log.debug("Write failure for {} is one a person can fix: {}", target, fixable.isPresent());
        fixable.ifPresent(error -> log.warn("Writing {} failed: {}", target, error.details(), failure));
        return fixable;
    }
}
