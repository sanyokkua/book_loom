package ua.bookloom.pipeline.dial;

import java.util.Objects;
import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;

/**
 * What one quality-dial setting means for a run's mechanics.
 *
 * @param repair what a segment whose draft fails a check is given
 * @param reviewPasses the dial's reviewer-pass figure, which no run reads any more (task 14.8 retires it with the
 *     dial table)
 * @param backwardRevision whether the backward-revision pass runs after the last segment of the book (until group 14)
 * @param proofreadCoverage which decided segments the Proofread stage reads
 */
public record DialParameters(
        RepairMode repair, int reviewPasses, boolean backwardRevision, ProofreadCoverage proofreadCoverage)
        implements DialProfile {

    /** Rejects a missing repair mode. */
    public DialParameters {
        Objects.requireNonNull(repair, "repair");
        Objects.requireNonNull(proofreadCoverage, "proofreadCoverage");
    }

    /**
     * Looks the dial's row up.
     *
     * @param dial the selected dial; never null
     * @return the row for {@code dial}
     */
    public static DialParameters of(final QualityDial dial) {
        return switch (Objects.requireNonNull(dial, "dial")) {
            case FAST -> new DialParameters(RepairMode.MARKS_ONLY, 0, false, ProofreadCoverage.NONE);
            case BALANCED -> new DialParameters(RepairMode.ONE, 1, false, ProofreadCoverage.MARKED_AND_DOUBTFUL);
            case MAX ->
                new DialParameters(RepairMode.ONE, 2, true, ProofreadCoverage.MARKED_AND_DOUBTFUL_THEN_EVERY_PARAGRAPH);
        };
    }
}
