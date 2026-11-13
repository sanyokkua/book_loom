package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * How an export reports its work.
 *
 * @param progress the workflow steps the export may finish
 * @param activities where the export is registered as work
 */
public record ExportWork(WorkflowProgress progress, ActivityTracker activities) {

    /** Rejects a missing part. */
    @Inject
    public ExportWork {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(activities, "activities");
    }
}
