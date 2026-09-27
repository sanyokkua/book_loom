package ua.bookloom.api.project;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The rolling summary of a book so far, kept to give later chunks context without replaying the whole book
 * ({@code specs/translation-pipeline/spec.md} "Keep a rolling summary of the book so far").
 *
 * @param projectId the owning project's id
 * @param unitId the unit this summary was last updated for, or null when project-wide
 * @param source the summary text in the source language
 * @param target the summary text in the target language
 * @param version this summary's version, incremented on each update
 * @param lastSummarizedKey the last segment key folded into this summary, or null when none has been
 * @param tokensSince the number of tokens of new text seen since this summary was last updated
 */
public record RollingSummary(
        String projectId,
        @Nullable String unitId,
        String source,
        String target,
        int version,
        @Nullable String lastSummarizedKey,
        int tokensSince) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public RollingSummary {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }
}
