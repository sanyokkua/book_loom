package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * What the run's brief and review mode set for every chunk.
 *
 * @param projectId the project the run translates
 * @param mode the run's review mode, whose threshold decides acceptance and which caps Manual chunks at one
 * @param dial the quality dial's mechanics
 * @param frame the run's languages, style sheet and foreign-passage policy
 * @param names the brief's name policy
 * @param window the context window in tokens the run's prompts and chunks are sized against; positive
 */
public record RunSettings(
        String projectId, ReviewMode mode, DialParameters dial, CallFrame frame, NamePolicy names, int window) {

    /** Rejects a missing component. */
    public RunSettings {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(names, "names");
        if (window <= 0) {
            throw new IllegalArgumentException("window must be positive: " + window);
        }
    }
}
