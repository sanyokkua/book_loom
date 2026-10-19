package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;

/**
 * Maps a job's returned {@link Result} onto the mirror's terminal state, and computes the segment counts an outcome
 * line reports even when no {@link JobReport} exists — a run stopped or failed before ever finishing a chunk still
 * has the last progress snapshot {@link RunSession} saw.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunOutcomes {

    /** How a returned result maps onto the mirror. */
    record Outcome(
            RunState state,
            @Nullable JobReport report,
            @Nullable AppError error) {}

    /** The counts an outcome line reports. */
    record Counts(int segments, int accepted, int flagged) {}

    static Outcome outcomeOf(final Result<JobReport> result) {
        final AppError refusal = result.error();
        if (refusal != null) {
            // A stop the person chose can come back as an error result too; it is the stopped state, not a failure.
            return refusal.code() == ErrorCode.cancelled
                    ? new Outcome(RunState.STOPPED, null, null)
                    : new Outcome(RunState.FAILED, null, refusal);
        }
        final JobReport report = Objects.requireNonNull(result.data(), "successful result data");
        return switch (report.end()) {
            case COMPLETED -> new Outcome(RunState.COMPLETED, report, null);
            case CANCELLED -> new Outcome(RunState.STOPPED, report, null);
            case FAILED -> new Outcome(RunState.FAILED, report, report.error());
            // JobReport's constructor rejects a non-terminal end, so no report can carry one.
            case NEW, RUNNING, PAUSED -> throw new IllegalStateException("a job report carried a non-final state");
        };
    }

    static Counts countsOf(final @Nullable JobReport report, final @Nullable JobProgress lastSeen) {
        if (report != null) {
            return new Counts(report.segments(), report.accepted(), report.flagged());
        }
        if (lastSeen == null) {
            return new Counts(0, 0, 0);
        }
        final RunFigures figures = RunFigures.from(lastSeen);
        return new Counts(figures.total(), figures.accepted(), figures.flagged());
    }

    /**
     * Logs how a run ended, with the counts its report or its last snapshot gives.
     *
     * @param outcome the run's outcome
     * @param lastSeen the last progress snapshot the session saw, or {@code null} for none
     */
    static void logEnded(final Outcome outcome, final @Nullable JobProgress lastSeen) {
        final Counts counts = countsOf(outcome.report(), lastSeen);
        final AppError error = outcome.error();
        log.info(
                "run ended {}: {} segments, {} accepted, {} flagged, error code {}",
                outcome.state(),
                counts.segments(),
                counts.accepted(),
                counts.flagged(),
                error == null ? "none" : error.code());
    }
}
