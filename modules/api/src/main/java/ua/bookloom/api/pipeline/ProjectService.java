package ua.bookloom.api.pipeline;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.BookBrief;

/**
 * Opens, plans and closes a project, the seam the Import and Book Brief screens call.
 */
public interface ProjectService {

    /**
     * Lists the stored projects with how much of each is already decided, so a screen can ask before a new import
     * discards work.
     *
     * @return one summary per stored project, most recently opened first; never null, empty when none is stored
     */
    Result<List<ProjectSummary>> list();

    /**
     * Inspects and, when the file can be opened, opens a candidate book and creates its project (or, for a file that
     * already has one, resets that project's segments and keeps its id).
     *
     * @param source the non-null candidate file
     * @return the inspection result and, when it opened, the project's id, profile and default brief; the id is
     *     {@code null} when the inspection refused the file
     */
    Result<ImportedBook> create(Path source);

    /**
     * Opens a stored project and keeps its brief and decided segments, where {@link #create} would reset the segments.
     *
     * @param projectId the non-null id of a stored project
     * @return {@link ProjectOpening.Opened} with the book and whether its run was interrupted;
     *     {@link ProjectOpening.BookFileMissing} or {@link ProjectOpening.BookFileChanged} as data; a {@code validation}
     *     error when no such project is stored
     */
    Result<ProjectOpening> open(String projectId);

    /**
     * Replaces a project's Book Brief.
     *
     * @param projectId the non-null owning project's id
     * @param brief the non-null replacement brief
     * @return {@link ProjectChange.Saved} with the stored project (an automatic name follows a changed language pair);
     *     {@link ProjectChange.PairInUse} or {@link ProjectChange.NameInUse} as data, with nothing stored, when the
     *     change would clash inside the book; a {@code validation} error for an unknown project
     */
    Result<ProjectChange> updateBrief(String projectId, BookBrief brief);

    /**
     * Renames a project.
     *
     * @param projectId the non-null id of a stored project
     * @param name the name the person typed (it then stays), or {@code null} to use the automatic name again
     * @return a {@link ProjectChange}; a {@code validation} error for an unknown project or a blank name
     */
    Result<ProjectChange> rename(String projectId, @Nullable String name);

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

    /**
     * Removes a project and everything stored for it; the book file, exported files and the book's other projects stay.
     *
     * @param projectId the non-null id of a stored project
     * @return {@code true} once removed; a {@code validation} error for an unknown project
     */
    Result<Boolean> remove(String projectId);

    /**
     * Creates a project of the same book for another target language, without importing the book again.
     *
     * @param projectId the non-null id of the project to copy the brief, the Glossary and the Recurring terms from
     * @param target the non-null target language tag
     * @param name the name the person typed, or {@code null} for the automatic one
     * @return {@link ProjectChange.Saved} with the new project, or a clash as data with nothing stored; a
     *     {@code validation} error for an unknown project
     */
    Result<ProjectChange> translateInto(String projectId, String target, @Nullable String name);

    /**
     * Points a project's book, and every project of it, at another file when its content hash matches.
     *
     * @param projectId the non-null id of a stored project
     * @param chosen the non-null file the person found
     * @return {@link BookRelocation.Relocated} or {@link BookRelocation.DifferentBook}; a {@code validation} error for
     *     an unknown project
     */
    Result<BookRelocation> relocate(String projectId, Path chosen);
}
