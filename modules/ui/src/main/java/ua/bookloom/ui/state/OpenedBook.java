package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NarratorHint;

/**
 * The book the window has open: the stored project it became, with what the import found about it. The source path is
 * kept because the project does not tell the window where the file lives and the destination proposal starts from it.
 *
 * @param projectId the stored project's id, which every later call about this book names
 * @param source the file the book was imported from
 * @param inspection what the pre-open inspection found
 * @param profile the opened book's profile, or {@code null} when the import answered without one
 * @param brief the brief the project was created with
 * @param narratorHint what the source text suggests about the narrator, or {@code null} when nothing was found; the Book Brief
 *     preselects "First person" from it (the person can change it), and it never sets the narrator's gender
 */
public record OpenedBook(
        String projectId,
        Path source,
        BookInspection inspection,
        @Nullable BookProfile profile,
        BookBrief brief,
        @Nullable NarratorHint narratorHint) {

    /** Rejects a missing id, source, inspection or brief. */
    public OpenedBook {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(inspection, "inspection");
        Objects.requireNonNull(brief, "brief");
    }

    /** A book that came with no hint about its narrator. */
    public OpenedBook(
            final String projectId,
            final Path source,
            final BookInspection inspection,
            @Nullable final BookProfile profile,
            final BookBrief brief) {
        this(projectId, source, inspection, profile, brief, null);
    }
}
