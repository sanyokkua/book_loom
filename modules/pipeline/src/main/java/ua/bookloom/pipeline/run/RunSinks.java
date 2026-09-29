package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.function.Consumer;
import ua.bookloom.api.pipeline.JobEvent;

/**
 * Where the run's decisions go.
 *
 * @param pending the decisions not yet committed
 * @param recorder the run's own counts and state
 * @param emit the receiver of each decision event
 * @param boundaries the job's answer at every place the run may pause or stop
 */
public record RunSinks(PendingCommit pending, RunRecorder recorder, Consumer<JobEvent> emit, RunBoundaries boundaries) {

    /** Rejects a missing sink. */
    public RunSinks {
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(recorder, "recorder");
        Objects.requireNonNull(emit, "emit");
        Objects.requireNonNull(boundaries, "boundaries");
    }
}
