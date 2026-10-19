package ua.bookloom.api.persistence;

import java.util.List;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Deferral;

/**
 * Stores a project's deferred segment decisions
 * ({@code specs/translation-pipeline/spec.md} "Record deferrals and revise backwards on Max").
 */
public interface DeferralRepository {

    /**
     * Adds a deferral, idempotent on project, segment, reason and waiting-on — adding one already open for that
     * combination is a no-op that returns the existing deferral.
     *
     * @param deferral the non-null deferral to add
     * @return the added or already-open deferral
     */
    Result<Deferral> add(Deferral deferral);

    /**
     * Lists a project's open (unresolved) deferrals.
     *
     * @param projectId the non-null project id
     * @return the open deferrals; never null, empty when none are open
     */
    Result<List<Deferral>> open(String projectId);

    /**
     * Resolves a deferral, removing it from the open set.
     *
     * @param projectId the non-null project id
     * @param deferralId the non-null deferral id
     * @return {@code true} when a deferral was resolved, {@code false} when no open deferral held that id
     */
    Result<Boolean> resolve(String projectId, String deferralId);
}
