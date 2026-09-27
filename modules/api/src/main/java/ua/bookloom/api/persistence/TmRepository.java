package ua.bookloom.api.persistence;

import java.util.List;
import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.TmEntry;

/**
 * Stores and reads a project's translation memory, keyed by source hash and context key
 * ({@code specs/translation-pipeline/spec.md} "Reuse the translation memory only where its context matches").
 */
public interface TmRepository {

    /**
     * Stores a translation-memory entry, one per project, source hash and context key — a repeated put for the
     * same key replaces the prior entry.
     *
     * @param entry the non-null entry to store
     * @return the stored entry
     */
    Result<TmEntry> put(TmEntry entry);

    /**
     * Finds every entry for an exact source hash, across all context keys.
     *
     * @param projectId the non-null project id
     * @param sourceHash the non-null source hash
     * @return the matching entries; never null, empty when none match
     */
    Result<List<TmEntry>> exact(String projectId, String sourceHash);

    /**
     * Finds the entry for an exact source hash under one context key.
     *
     * @param projectId the non-null project id
     * @param sourceHash the non-null source hash
     * @param contextKey the non-null context key
     * @return the matching entry if found, or empty when none matches
     */
    Result<Optional<TmEntry>> context(String projectId, String sourceHash, String contextKey);

    /**
     * Finds entries whose masked source length lies within a character band, for fuzzy-match scoring by the
     * pipeline.
     *
     * @param projectId the non-null project id
     * @param minChars the inclusive lower bound on source length
     * @param maxChars the inclusive upper bound on source length
     * @return the candidate entries; never null, empty when none lie in the band
     */
    Result<List<TmEntry>> candidates(String projectId, int minChars, int maxChars);
}
