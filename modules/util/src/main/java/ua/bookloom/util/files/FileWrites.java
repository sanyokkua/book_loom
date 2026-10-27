package ua.bookloom.util.files;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Writes a file so that a reader never sees it half written: the text goes into a new hidden file beside the target,
 * which then takes the target's place in one move.
 *
 * <p>The hidden file is always a new one, under a name nothing in the folder holds, so a file already standing there —
 * a person's own, a link, another write's — is never written over and never deleted.
 */
// Checkstyle cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileWrites {

    /** How many names beside the target are tried before a reservation gives up. */
    private static final int MAX_TEMPORARY_NAMES = 100;

    private static final String TEMPORARY_PREFIX = ".bookloom-";

    /**
     * The hidden name the {@code attempt}-th reservation beside {@code target} tries; it ends with the target's own
     * name, so a compound suffix such as {@code .fb2.zip} still tells a reader the format.
     *
     * @param target the file the temporary one is for; must have a file name
     * @param attempt the zero-based attempt
     * @return the sibling path, which may or may not exist
     */
    public static Path temporaryName(final Path target, final int attempt) {
        final Path fileName = Objects.requireNonNull(target.getFileName(), "target file name");
        return target.resolveSibling(TEMPORARY_PREFIX + attempt + "-" + fileName);
    }

    /**
     * Creates a new, empty temporary file beside {@code target}, skipping every name that already exists.
     *
     * @param target the file the temporary one is for
     * @return the created file, which belongs to the caller alone
     * @throws UncheckedIOException carrying the creation's own failure — a missing folder, a refused permission, a
     *     read-only place — or a {@link FileAlreadyExistsException} when every name was taken
     */
    public static Path reserveBeside(final Path target) {
        Objects.requireNonNull(target, "target");
        IOException last = new FileAlreadyExistsException(target.toString());
        for (int attempt = 0; attempt < MAX_TEMPORARY_NAMES; attempt++) {
            try {
                return Files.createFile(temporaryName(target, attempt));
            } catch (FileAlreadyExistsException taken) {
                last = taken;
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        throw new UncheckedIOException(last);
    }

    /**
     * Writes {@code text} as UTF-8 into {@code target}, replacing a file there only once the whole text is written.
     *
     * @param target the file to write
     * @param text the whole content
     * @throws UncheckedIOException carrying the write's or the move's own failure; the temporary file is removed
     */
    public static void replace(final Path target, final String text) {
        Objects.requireNonNull(text, "text");
        final Path temporary = reserveBeside(target);
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            moveOnto(temporary, target);
        } catch (IOException failure) {
            deleteQuietly(temporary, failure);
            throw new UncheckedIOException(failure);
        }
    }

    private static void moveOnto(final Path temporary, final Path target) throws IOException {
        try {
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(final Path temporary, final IOException primary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanup) {
            primary.addSuppressed(cleanup);
        }
    }
}
