package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * Everything the runner and the screens need to know about the run being started, so that a new fact about a run is a
 * new component here and not a new parameter on every start.
 *
 * @param projectId the stored project the run translates
 * @param fileName the name of the book file, shown while the run lives
 * @param reviewMode how much of the outcome the person confirms
 * @param dial the brief's speed and quality choice
 * @param selection the provider and model, for the log and the run's display
 */
public record RunContext(
        String projectId, String fileName, ReviewMode reviewMode, QualityDial dial, ModelSelection selection) {

    /** Rejects a missing component. */
    public RunContext {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(reviewMode, "reviewMode");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(selection, "selection");
    }
}
