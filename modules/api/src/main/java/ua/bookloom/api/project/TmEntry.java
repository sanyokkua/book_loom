package ua.bookloom.api.project;

import java.util.Objects;

/**
 * One translation-memory entry, keyed within a project by its source hash and context key
 * ({@code specs/translation-pipeline/spec.md} "Reuse the translation memory only where its context matches").
 *
 * @param id the entry's stable id
 * @param projectId the owning project's id
 * @param sourceHash the SHA-256 hash over the pre-mask source text
 * @param contextKey the key identifying the context this entry was translated under
 * @param sourceInner the display text of the source — placeholder tokens removed, whitespace normalized — which is
 *     the form the fuzzy comparison and the code-point length band of {@code TmRepository.candidates} run on
 * @param targetInner the masked target text
 */
public record TmEntry(
        String id, String projectId, String sourceHash, String contextKey, String sourceInner, String targetInner) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public TmEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(contextKey, "contextKey");
        Objects.requireNonNull(sourceInner, "sourceInner");
        Objects.requireNonNull(targetInner, "targetInner");
    }
}
