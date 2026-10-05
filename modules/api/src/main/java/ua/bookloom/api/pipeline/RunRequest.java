package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A request to start or resume a project's run under a chosen review mode.
 *
 * @param projectId the project to run
 * @param mode the review mode governing the run's pause points and acceptance threshold
 * @param detectedContext the context length in tokens the provider reported for the run's model, or null when it said
 *     nothing or was not asked; the run sizes its prompts against the smaller of this and its default window
 */
public record RunRequest(
        String projectId, ReviewMode mode, @Nullable Integer detectedContext) {

    /** Rejects a request without a project id or review mode. */
    public RunRequest {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(mode, "mode");
        if (detectedContext != null && detectedContext <= 0) {
            throw new IllegalArgumentException("detectedContext must be positive: " + detectedContext);
        }
    }

    /**
     * A request with no detected context length, so the run sizes against its default window.
     *
     * @param projectId the project to run
     * @param mode the review mode
     */
    public RunRequest(final String projectId, final ReviewMode mode) {
        this(projectId, mode, null);
    }
}
