package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ProjectSummary;

/** Finds, among the stored projects, the one a file would reuse, so the import screens ask before replacing it. */
// Checkstyle reads the source before Lombok adds the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EarlierProjects {

    // The list arrives newest first, so the first match is the most recently opened one.
    static Optional<ProjectSummary> find(final List<ProjectSummary> projects, final Path source) {
        final Path wanted = source.toAbsolutePath().normalize();
        return projects.stream()
                .filter(summary -> summary.project().source().equals(wanted))
                .findFirst();
    }
}
