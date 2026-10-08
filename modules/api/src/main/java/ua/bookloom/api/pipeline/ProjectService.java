package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.List;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;

/**
 * Opens, plans and closes a project, the seam the Import and Book Brief screens call.
 */
public interface ProjectService {

    /**
     * Inspects and, when the file can be opened, opens a candidate book and creates its project.
     *
     * @param source the non-null candidate file
     * @return the inspection result and, when it opened, the new project's profile and default brief
     */
    Result<ImportedBook> importBook(Path source);

    /**
     * Replaces a project's Book Brief.
     *
     * @param projectId the non-null owning project's id
     * @param brief the non-null replacement brief
     * @return the project with its updated brief
     */
    Result<Project> updateBrief(String projectId, BookBrief brief);

    /**
     * Computes how a run would chunk a project's units before it starts.
     *
     * @param projectId the non-null project id
     * @return the computed plan
     */
    Result<BookPlan> plan(String projectId);

    /**
     * Lists one unit's source segments with the chunk a run would pack each into, from the current brief; it makes no
     * model call and works before any run.
     *
     * @param projectId the non-null project id
     * @param unitId the non-null id of one unit of the project's book; a whole-book listing is not offered
     * @return the unit's segments in document order; a {@code validation} error for an unknown project or unit
     */
    Result<List<SegmentPreview>> segments(String projectId, String unitId);

    /**
     * Compares a no-op reassembly of the project's source against the source itself.
     *
     * @param projectId the non-null project id
     * @return the round-trip comparison
     */
    Result<RoundTripReport> roundTrip(String projectId);

    /**
     * Releases the project's opened book, called from Import's Cancel and before a second import.
     *
     * @param projectId the non-null project id
     * @return {@code true} once the book is released
     */
    Result<Boolean> close(String projectId);
}
