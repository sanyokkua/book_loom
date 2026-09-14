package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces the terminal report for a job.
 *
 * @param report the non-null terminal report
 */
public record Finished(JobReport report) implements JobEvent {

    /** Rejects a terminal event without its report. */
    public Finished {
        Objects.requireNonNull(report, "report");
    }
}
