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
 * @param reviewPasses how many times the reviewer reads each chunk: none for Fast, one for Balanced, two for Max, the
 *     second with a narrower checklist
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

    /**
     * The same mechanics with another number of reviewer passes; arm 7a's OFF choice passes zero.
     *
     * @param passes how many times the reviewer reads each chunk; zero for none
     * @return the copy
     */
    public DialParameters withReviewerPasses(final int passes) {
        return new DialParameters(repair, passes, backwardRevision, proofreadCoverage);
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
