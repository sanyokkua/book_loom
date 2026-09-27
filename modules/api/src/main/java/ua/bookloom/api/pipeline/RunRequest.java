package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A request to start or resume a project's run under a chosen review mode.
 *
 * @param projectId the project to run
 * @param mode the review mode governing the run's pause points and acceptance threshold
 */
public record RunRequest(String projectId, ReviewMode mode) {

    /** Rejects a request without a project id or review mode. */
    public RunRequest {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(mode, "mode");
    }
}
