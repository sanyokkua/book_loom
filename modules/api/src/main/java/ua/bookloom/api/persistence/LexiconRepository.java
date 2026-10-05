package ua.bookloom.api.persistence;

import java.util.List;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.LexiconEntry;

/**
 * Stores a project's recurring-term lexicon: the closed list of key terms a run asks the model to report renderings
 * for, with the verified renderings and their counts ({@code specs/glossary/spec.md} "Keep a lexicon of recurring
 * terms"). Terms are matched ignoring case and surrounding space.
 */
public interface LexiconRepository {

    /**
     * Lists a project's lexicon.
     *
     * @param projectId the non-null project id
     * @return the entries; never null, empty when the project has none
     */
    Result<List<LexiconEntry>> all(String projectId);

    /**
     * Adds an entry or replaces the one held for the same term.
     *
     * @param entry the non-null entry to store
     * @return the stored entry
     */
    Result<LexiconEntry> put(LexiconEntry entry);

    /**
     * Counts one more use of a rendering for a term, as one step so two drafts never lose a count; a term the lexicon
     * does not hold is added.
     *
     * @param projectId the non-null project id
     * @param term the non-null source term
     * @param rendering the non-blank rendering a verified pair named
     * @return the entry as it now stands
     */
    Result<LexiconEntry> record(String projectId, String term, String rendering);

    /**
     * Removes a term.
     *
     * @param projectId the non-null project id
     * @param term the non-null source term
     * @return {@code true} when an entry was removed, {@code false} when none held the term
     */
    Result<Boolean> remove(String projectId, String term);
}
