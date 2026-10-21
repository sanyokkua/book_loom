package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;

/** Test shortcuts over {@link ProjectService}: what {@code list} and {@code open} say about one file's project. */
public final class ProjectServices {

    private ProjectServices() {}

    /** The decided-segment count {@code list} reports for the project whose source is {@code source}; 0 when none. */
    public static int decidedOf(final ProjectService service, final Path source) {
        final Result<List<ProjectSummary>> listed = service.list();
        final Path wanted = source.toAbsolutePath().normalize();
        return Objects.requireNonNull(listed.data(), () -> "list: " + listed.error()).stream()
                .filter(summary -> summary.project().source().equals(wanted))
                .mapToInt(ProjectSummary::decidedSegments)
                .findFirst()
                .orElse(0);
    }

    /** Opens again the project that an earlier import answered. */
    public static Result<ImportedBook> reopen(final ProjectService service, final ImportedBook created) {
        return service.open(Objects.requireNonNull(created.projectId(), "project id"));
    }
}
