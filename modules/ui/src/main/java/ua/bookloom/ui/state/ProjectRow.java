package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.ui.ViewNames;

/**
 * One translation on the Projects screen.
 *
 * @param summary the stored project with its counts, latest run and last export
 * @param percent decided segments of the stored ones, rounded down, from 0 to 100
 * @param status what the row's chip says
 * @param open whether this is the project open in the window
 * @param step the screen Continue opens it on
 * @param providerName the display name of the latest run's provider, or an empty text when no run named one
 */
public record ProjectRow(
        ProjectSummary summary, int percent, ProjectStatus status, boolean open, ViewNames step, String providerName) {

    /** Rejects a missing part. */
    public ProjectRow {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(providerName, "providerName");
    }
}
