package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.SideFile;

/**
 * The refusals beside Save to that need no disk: they are decided from the two paths alone, so they stand the moment
 * a path is typed. The export job repeats them before it writes, because this class only spares a person the wait.
 */
@Slf4j
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExportPathRules {

    private static final String ZIP = ".zip";

    /** Why a destination cannot take the book. */
    enum Refusal {
        /** The destination is the source file, which a write would overwrite. */
        SOURCE_ITSELF,
        /** The destination's file type differs from the source's, and a book is only written in its own format. */
        CHANGED_TYPE
    }

    /**
     * Decides whether a destination is refused.
     *
     * @param source the source file the project was opened from
     * @param destination the path the person chose
     * @return the reason, or empty when the destination is acceptable; {@code .md} and {@code .markdown} count as one
     *     type while {@code .fb2.zip} and {@code .fb2} do not
     */
    static Optional<Refusal> refusal(final Path source, final Path destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (source.normalize().equals(destination.normalize())) {
            log.debug("destination {} refused: it is the source", destination);
            return Optional.of(Refusal.SOURCE_ITSELF);
        }
        final Optional<FileType> wanted = formatOf(source);
        if (wanted.isPresent() && !wanted.equals(formatOf(destination))) {
            log.debug("destination {} refused: its type is not {}", destination, wanted.get());
            return Optional.of(Refusal.CHANGED_TYPE);
        }
        return Optional.empty();
    }

    /**
     * Lists the files an export would create, in the order a refusal names them.
     *
     * @param destination the book's path
     * @param format the book's format, the source's
     * @param sideFiles the side files chosen
     * @return the book's path, then each chosen side file's path beside it
     */
    static List<Path> writtenPaths(final Path destination, final BookFormat format, final Set<SideFile> sideFiles) {
        final List<Path> paths = new ArrayList<>();
        paths.add(destination);
        for (final SideFile file : SideFile.values()) {
            if (sideFiles.contains(file)) {
                paths.add(file.pathBeside(destination, format));
            }
        }
        return paths;
    }

    // A zipped FictionBook is a different file type from a bare one although both are FB2.
    private static Optional<FileType> formatOf(final Path path) {
        final Path name = path.getFileName();
        if (name == null) {
            return Optional.empty();
        }
        final String text = name.toString();
        return BookFormat.ofFileName(text)
                .map(format ->
                        new FileType(format, text.toLowerCase(Locale.ROOT).endsWith(ZIP)));
    }

    private record FileType(BookFormat format, boolean zipped) {}
}
