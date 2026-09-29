package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookInspection;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.project.BookBrief;

/**
 * The book the window has open: the stored project it became, with what the import found about it. The source path is
 * kept because the project does not tell the window where the file lives and the destination proposal starts from it.
 *
 * @param projectId the stored project's id, which every later call about this book names
 * @param source the file the book was imported from
 * @param inspection what the pre-open inspection found
 * @param profile the opened book's profile, or {@code null} when the import answered without one
 * @param brief the brief the project was created with
 */
public record OpenedBook(
        String projectId,
        Path source,
        BookInspection inspection,
        @Nullable BookProfile profile,
        BookBrief brief) {

    /** Rejects a missing id, source, inspection or brief. */
    public OpenedBook {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(inspection, "inspection");
        Objects.requireNonNull(brief, "brief");
    }
}
