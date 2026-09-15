package ua.bookloom.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Resolves aliases without requiring the prospective export file to exist yet. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class ExportPathAliases {

    static boolean aliases(final Path first, final Path second) {
        final Path absoluteFirst = first.toAbsolutePath();
        final Path absoluteSecond = second.toAbsolutePath();
        if (absoluteFirst.equals(absoluteSecond)) {
            return true;
        }
        if (isSameExistingFile(first, second)) {
            return true;
        }
        final Optional<Path> resolvedFirst = resolveProspective(absoluteFirst);
        final Optional<Path> resolvedSecond = resolveProspective(absoluteSecond);
        return resolvedFirst.isPresent() && resolvedFirst.equals(resolvedSecond);
    }

    private static boolean isSameExistingFile(final Path first, final Path second) {
        try {
            return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static Optional<Path> resolveProspective(final Path path) {
        try {
            Path current = path;
            final Set<Path> followedLinks = new HashSet<>();
            while (Files.isSymbolicLink(current)) {
                final Path linkIdentity = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!followedLinks.add(linkIdentity)) {
                    log.debug("Stopped prospective export-path resolution at symbolic-link cycle {}", linkIdentity);
                    return Optional.empty();
                }
                current = resolveLink(current, Files.readSymbolicLink(current));
            }
            if (Files.exists(current)) {
                return Optional.of(current.toRealPath());
            }
            return resolveThroughExistingParent(current);
        } catch (FileSystemLoopException | NoSuchFileException cause) {
            log.debug("Prospective export path {} could not be resolved", path, cause);
            return Optional.empty();
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static Path resolveLink(final Path link, final Path target) {
        if (target.isAbsolute()) {
            return target;
        }
        return Objects.requireNonNull(link.getParent(), "symbolic-link parent").resolve(target);
    }

    private static Optional<Path> resolveThroughExistingParent(final Path path) {
        final Path parent = path.getParent();
        final Path fileName = path.getFileName();
        if (parent == null || fileName == null || !Files.exists(parent)) {
            log.debug("Prospective export path {} has no resolvable existing parent", path);
            return Optional.empty();
        }
        try {
            return Optional.of(parent.toRealPath().resolve(fileName));
        } catch (FileSystemLoopException | NoSuchFileException cause) {
            log.debug("Prospective export path {} could not be resolved through its parent", path, cause);
            return Optional.empty();
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
