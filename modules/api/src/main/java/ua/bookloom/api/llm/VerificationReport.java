package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable outcomes from the stages attempted during provider verification.
 *
 * @param stages the outcomes in the order they ran
 * @param engine the server engine the connection stage found, or null when the check did not detect one (a run's
 *     recovery probe, an unreachable server, a failed connection)
 */
public record VerificationReport(
        List<StageOutcome> stages, @Nullable EngineDetection engine) {

    /** Rejects a missing stage list and defensively copies the supplied outcomes. */
    public VerificationReport {
        Objects.requireNonNull(stages, "stages");
        stages = List.copyOf(stages);
    }

    /** A report with no engine detected. */
    public VerificationReport(final List<StageOutcome> stages) {
        this(stages, null);
    }

    /**
     * Reports whether verification has no failed stage.
     *
     * @return false if any stage failed; true otherwise
     */
    public boolean isPassed() {
        return stages.stream().noneMatch(stage -> stage.status() == StageStatus.FAILED);
    }
}
