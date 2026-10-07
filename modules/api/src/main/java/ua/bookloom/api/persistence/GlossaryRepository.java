package ua.bookloom.api.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.GlossaryEntry;

/**
 * Stores a project's glossary and remembers, for the session, a term the person removed — so a name the pipeline
 * proposes again after removal is not silently re-added
 * ({@code specs/glossary/spec.md} "Keep the person's entries and removals when names are proposed").
 */
public interface GlossaryRepository {

    /**
     * Adds a glossary entry. Used by both the Add-term dialog and CSV import.
     *
     * <p>Adding a term this session's removal memory holds clears that memory, so the new entry sticks.
     *
     * @param entry the non-null entry to add
     * @return the added entry, or {@code validation} when the term already exists, ignoring case, composition, a trailing
     *     possessive and edge punctuation
     */
    Result<GlossaryEntry> add(GlossaryEntry entry);

    /**
     * Updates an existing glossary entry.
     *
     * @param entry the non-null replacement entry, matched by id
     * @return the updated entry, or {@code validation} when no entry holds that id
     */
    Result<GlossaryEntry> update(GlossaryEntry entry);

    /**
     * Changes the entry held for a term in one atomic step, so a change another thread made to the same entry a moment
     * earlier is never overwritten by a stale copy: the change is applied to the entry as it is at that instant.
     *
     * @param projectId the non-null project id
     * @param term the non-null term, matched as {@link #add} compares terms
     * @param change the non-null change; it receives the held entry and returns the entry to keep, with the same id,
     *     or its argument itself to leave the entry as it is
     * @return the entry now held, or empty when no entry holds the term
     */
    Result<Optional<GlossaryEntry>> update(String projectId, String term, UnaryOperator<GlossaryEntry> change);

    /**
     * Removes a glossary entry and remembers its term as removed for the session.
     *
     * @param projectId the non-null project id
     * @param entryId the non-null entry id
     * @return {@code true} when an entry was removed, {@code false} when no entry held that id
     */
    Result<Boolean> remove(String projectId, String entryId);

    /**
     * Lists a project's glossary entries.
     *
     * @param projectId the non-null project id
     * @return the entries; never null, empty when the project has none
     */
    Result<List<GlossaryEntry>> all(String projectId);

    /**
     * Finds a glossary entry by term, compared as {@link #add} compares terms.
     *
     * @param projectId the non-null project id
     * @param term the non-null term to match
     * @return the matching entry if found, or empty when none matches
     */
    Result<Optional<GlossaryEntry>> findByTerm(String projectId, String term);

    /**
     * Reports whether a term was removed this session, compared as {@link #add} compares terms.
     *
     * @param projectId the non-null project id
     * @param term the non-null term to check
     * @return {@code true} when the term was removed this session and not re-added since
     */
    Result<Boolean> wasRemoved(String projectId, String term);
}
