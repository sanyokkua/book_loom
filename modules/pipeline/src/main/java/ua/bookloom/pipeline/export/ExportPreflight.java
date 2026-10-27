package ua.bookloom.pipeline.export;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.project.Project;

/**
 * The refusals an export makes before anything slow: no target language, a book that is not open, an occupied path
 * the person did not allow replacing, or a side file that would land on the source or the book.
 */
@Slf4j
final class ExportPreflight {

    private final ExportRequest request;
    private final List<SideFile> sideFiles;

    ExportPreflight(final ExportRequest request, final List<SideFile> sideFiles) {
        this.request = Objects.requireNonNull(request, "request");
        this.sideFiles = List.copyOf(Objects.requireNonNull(sideFiles, "sideFiles"));
    }

    /**
     * The first refusal the export meets.
     *
     * @param project the stored project
     * @param opened the project's open book, or null when it is not open
     * @return the refusal, or null when the export may go on
     */
    @Nullable
    AppError refusal(final Project project, @Nullable final Document opened) {
        final String targetLanguage = project.brief().targetLanguage();
        log.debug("export check=target-language value={}", targetLanguage);
        if (targetLanguage == null) {
            return refused("target-language", "No target language is chosen", "Choose the target language first.");
        }
        log.debug("export check=book-open outcome={}", opened == null ? "closed" : "open");
        if (opened == null) {
            return refused(
                    "book-open", "This project is not open", "No open project has this id; open the book again.");
        }
        if (isRefusedOccupied(request.destination())) {
            return refused(
                    "destination-exists",
                    "This destination already exists",
                    "Allow replacing it, or choose another file.");
        }
        return occupiedSideFile(project.source(), opened);
    }

    private @Nullable AppError occupiedSideFile(final Path source, final Document opened) {
        for (final SideFile sideFile : sideFiles) {
            final Path path = sideFile.pathBeside(request.destination(), opened.format());
            if (isBookItself(path, source)) {
                return refused(
                        "side-file-is-book",
                        "A side file would replace a book",
                        path.getFileName() + " is the source book or the translated book. Choose another file.");
            }
            if (isRefusedOccupied(path)) {
                return refused(
                        "side-file-exists",
                        "A side file already exists",
                        path.getFileName() + " already exists. Allow replacing it, or choose another file.");
            }
        }
        return null;
    }

    // The same alias test the book's own temporary file gets: one normalized path, the same existing file, or one link
    // target — so neither a name clash nor a symbolic link lets a side file land on a book.
    private boolean isBookItself(final Path sideFile, final Path source) {
        final Path normalized = sideFile.toAbsolutePath().normalize();
        final boolean isSource =
                ExportPathAliases.aliases(normalized, source.toAbsolutePath().normalize());
        final boolean isDestination = ExportPathAliases.aliases(
                normalized, request.destination().toAbsolutePath().normalize());
        log.debug(
                "export check=side-file-is-book path={} isSource={} isDestination={}",
                sideFile,
                isSource,
                isDestination);
        return isSource || isDestination;
    }

    private boolean isRefusedOccupied(final Path path) {
        final boolean occupied = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        log.debug(
                "export check=path-free path={} outcome={} overwrite={}",
                path,
                occupied ? "occupied" : "free",
                request.overwrite());
        return occupied && !request.overwrite();
    }

    private static AppError refused(final String check, final String title, final String message) {
        log.warn("Refused export check={} code={}", check, ErrorCode.validation);
        return AppError.of(ErrorCode.validation, title, message);
    }
}
