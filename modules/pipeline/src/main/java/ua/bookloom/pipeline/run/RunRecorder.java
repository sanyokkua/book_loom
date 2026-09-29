package ua.bookloom.pipeline.run;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.RunRecord;

/**
 * Writes the run's state — running, paused, then completed, cancelled or failed — and nothing else, because the
 * review desk allows or refuses a retry by reading it. A write that fails is logged and does not stop the run: the
 * run's own decisions are stored elsewhere and stay valid.
 *
 * <p>Used from the job thread only, which is why the counters are plain fields.
 */
@Slf4j
public final class RunRecorder {

    private final RunRepository runs;
    private final String runId;
    private final String projectId;
    private final Clock clock;
    private Instant startedAt;
    private int accepted;
    private int flagged;

    /**
     * Creates a recorder for one run.
     *
     * @param runs the non-null store the state is written to
     * @param runId the non-null id of the run
     * @param projectId the non-null id of the project it runs
     * @param clock the non-null clock the start and end are read from
     */
    public RunRecorder(final RunRepository runs, final String runId, final String projectId, final Clock clock) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.runId = Objects.requireNonNull(runId, "runId");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = clock.instant();
    }

    /** Writes {@code RUNNING} with the moment the run began. */
    public void started() {
        startedAt = clock.instant();
        write(JobState.RUNNING, null);
    }

    /** Writes {@code PAUSED}. */
    public void paused() {
        write(JobState.PAUSED, null);
    }

    /** Writes {@code RUNNING} again after a pause. */
    public void resumed() {
        write(JobState.RUNNING, null);
    }

    /**
     * Counts a segment this run decided, so the record's totals are the run's own and not the project's.
     *
     * @param status the non-null status the run gave the segment
     */
    public void decided(final SegmentStatus status) {
        Objects.requireNonNull(status, "status");
        switch (status) {
            case ACCEPTED, REVISED -> accepted++;
            case FLAGGED -> flagged++;
            case PENDING -> log.debug("A pending segment is not a decision runId={}", runId);
        }
    }

    /**
     * Writes the terminal state with the moment the run ended.
     *
     * @param end the non-null terminal state
     */
    public void ended(final JobState end) {
        Objects.requireNonNull(end, "end");
        write(end, clock.instant());
    }

    private void write(final JobState state, @Nullable final Instant endedAt) {
        log.debug("Writing run state runId={} state={}", runId, state);
        final Result<RunRecord> saved =
                runs.save(new RunRecord(runId, projectId, startedAt, endedAt, state, accepted, flagged));
        if (saved.isErr()) {
            log.warn("The run state could not be stored runId={} state={}", runId, state);
        }
    }
}
