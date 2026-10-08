package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * A request to export a project's translated book.
 *
 * @param projectId the project to export
 * @param destination the file to write the translated book to
 * @param overwrite whether an existing file at {@code destination} (or a chosen side-file path) may be replaced
 * @param sideFiles which side files to write beside the book
 * @param consistencyPass whether to run the final consistency pass before writing
 * @param consistencyScope which paragraphs the pass checks against their neighbours
 */
public record ExportRequest(
        String projectId,
        Path destination,
        boolean overwrite,
        Set<SideFile> sideFiles,
        boolean consistencyPass,
        ConsistencyScope consistencyScope) {

    /** Rejects a request without its project id, destination or scope and defensively copies {@code sideFiles}. */
    public ExportRequest {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(sideFiles, "sideFiles");
        Objects.requireNonNull(consistencyScope, "consistencyScope");
        sideFiles = Set.copyOf(sideFiles);
    }

    /** A request whose pass checks the paragraphs the quality dial chooses. */
    public ExportRequest(
            final String projectId,
            final Path destination,
            final boolean overwrite,
            final Set<SideFile> sideFiles,
            final boolean consistencyPass) {
        this(projectId, destination, overwrite, sideFiles, consistencyPass, ConsistencyScope.BY_DIAL);
    }
}
