package ua.bookloom.pipeline.dial;

import java.util.Objects;
import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;

/**
 * What one quality-dial setting means for a run's mechanics.
 *
 * @param repair what a segment whose draft fails a check is given
 * @param reviewPasses how many times the reviewer reads each chunk: none for Fast, one for Balanced, two for Max, the
 *     second with a narrower checklist
 * @param backwardRevision whether the backward-revision pass runs after the last segment of the book
 * @param llmSummary whether the rolling summary is written by the model
 */
public record DialParameters(RepairMode repair, int reviewPasses, boolean backwardRevision, boolean llmSummary)
        implements DialProfile {

    /** Rejects a missing repair mode. */
    public DialParameters {
        Objects.requireNonNull(repair, "repair");
    }

    /**
     * Looks the dial's row up.
     *
     * @param dial the selected dial; never null
     * @return the row for {@code dial}
     */
    public static DialParameters of(final QualityDial dial) {
        return switch (Objects.requireNonNull(dial, "dial")) {
            case FAST -> new DialParameters(RepairMode.MARKS_ONLY, 0, false, false);
            case BALANCED -> new DialParameters(RepairMode.ONE, 1, false, false);
            case MAX -> new DialParameters(RepairMode.ONE, 2, true, true);
        };
    }

    /**
     * Whether the reviewer reads each chunk.
     *
     * @return {@code true} for every dial but Fast
     */
    public boolean hasReviewer() {
        return reviewPasses > 0;
    }
}
