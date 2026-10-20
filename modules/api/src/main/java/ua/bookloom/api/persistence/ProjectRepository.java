package ua.bookloom.api.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.ExportRecord;
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

    /**
     * Lists every stored project.
     *
     * @return all projects in no guaranteed order; empty when none are stored
     */
    Result<List<Project>> all();

    /**
     * Finds the project of a book for one language pair.
     *
     * @param contentHash the non-null content hash of the book
     * @param sourceLanguage the source tag of the pair, or null for an Auto source
     * @param targetLanguage the target tag of the pair, or null when not yet chosen
     * @return a project of that book whose brief holds exactly that pair, or empty when none does
     */
    Result<Optional<Project>> findByPair(
            String contentHash, @Nullable String sourceLanguage, @Nullable String targetLanguage);

    /**
     * Finds the project of a book by its folded name.
     *
     * @param contentHash the non-null content hash of the book
     * @param nameKey the non-null key from {@link Project#keyOf(String)}
     * @return the book's project of that name, or empty; another book may hold the same name
     */
    Result<Optional<Project>> findByNameKey(String contentHash, String nameKey);

    /**
     * Points every project of a book at a new source file, for "Find the book…".
     *
     * @param contentHash the non-null content hash of the book
     * @param newPath the non-null path the book now lives at
     * @return the updated projects; empty when no project holds that book
     */
    Result<List<Project>> relocateBook(String contentHash, Path newPath);

    /**
     * Stores what a project's export wrote, replacing the one stored before.
     *
     * @param projectId the non-null project id
     * @param record the non-null record of the export
     * @return the stored record
     */
    Result<ExportRecord> recordExport(String projectId, ExportRecord record);

    /**
     * Reads what a project's last export wrote.
     *
     * @param projectId the non-null project id
     * @return the record, or empty when the project was never exported
     */
    Result<Optional<ExportRecord>> lastExport(String projectId);
}
