package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.ProjectSummary;

/** Finds the stored projects a dropped file already has, so importing it again is never offered silently. */
// Checkstyle reads the source before Lombok adds the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EarlierProjects {

    /** Every project whose book content has this hash, in list order; a copy under another name is the same book. */
    static List<ProjectSummary> matching(final List<ProjectSummary> projects, final String contentHash) {
        Objects.requireNonNull(projects, "projects");
        Objects.requireNonNull(contentHash, "contentHash");
        return projects.stream()
                .filter(summary -> summary.project().contentHash().equals(contentHash))
                .toList();
    }
}
