package ua.bookloom.api.persistence;

import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Project;

/**
 * Stores and retrieves a project, keyed by its stable id — one book kept as if there could be many
 * ({@code docs/adr/0034}).
 */
public interface ProjectRepository {

    /**
     * Saves a project, inserting or replacing it by id.
     *
     * @param project the non-null project to save
     * @return the saved project, or {@code validation} when {@code project} fails a storage-level invariant
     */
    Result<Project> save(Project project);

    /**
     * Finds a project by id.
     *
     * @param projectId the non-null project id
     * @return the project if found, or empty when no project holds that id; never a failure for "not found"
     */
    Result<Optional<Project>> find(String projectId);

    /**
     * Deletes a project by id.
     *
     * @param projectId the non-null project id
     * @return {@code true} when a project was deleted, {@code false} when no project held that id
     */
    Result<Boolean> delete(String projectId);
}
