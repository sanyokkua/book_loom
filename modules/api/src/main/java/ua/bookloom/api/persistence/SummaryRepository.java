package ua.bookloom.api.persistence;

import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.RollingSummary;

/**
 * Stores and reads a project's rolling summary
 * ({@code specs/translation-pipeline/spec.md} "Keep a rolling summary of the book so far").
 */
public interface SummaryRepository {

    /**
     * Saves a project's rolling summary, replacing any prior summary.
     *
     * @param summary the non-null summary to save
     * @return the saved summary
     */
    Result<RollingSummary> save(RollingSummary summary);

    /**
     * Finds a project's current rolling summary.
     *
     * @param projectId the non-null project id
     * @return the summary if one has been saved, or empty when none has
     */
    Result<Optional<RollingSummary>> latest(String projectId);
}
