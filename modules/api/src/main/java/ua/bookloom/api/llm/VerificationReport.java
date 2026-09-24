package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;

/** Immutable outcomes from the stages attempted during provider verification. */
public record VerificationReport(List<StageOutcome> stages) {

    /** Rejects a missing stage list and defensively copies the supplied outcomes. */
    public VerificationReport {
        Objects.requireNonNull(stages, "stages");
        stages = List.copyOf(stages);
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
