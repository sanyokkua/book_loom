package ua.bookloom.api.persistence;

import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.RunRecord;

/**
 * Stores a project's translation-run history — read by resume and by the review desk to allow or refuse a retry.
 */
public interface RunRepository {

    /**
     * Saves a run record, upserting by run id so every state change of one run lands in one record.
     *
     * @param run the non-null run record to save
     * @return the saved record
     */
    Result<RunRecord> save(RunRecord run);

    /**
     * Finds the run started last for a project.
     *
     * @param projectId the non-null project id
     * @return the most recently started run if one exists, or empty when the project has never run
     */
    Result<Optional<RunRecord>> latest(String projectId);
}
