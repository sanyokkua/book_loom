package ua.bookloom.pipeline.dial;

import java.util.Objects;
import ua.bookloom.api.pipeline.DialProfile;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.RepairMode;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * What one quality-dial setting means for a run's mechanics.
 *
 * @param precedingTargets how many accepted preceding targets accompany a draft
 * @param repair what a segment whose draft fails a check is given
 * @param reviewPasses how many times the reviewer reads each chunk: none for Fast, one for Balanced, two for Max, the
 *     second with a narrower checklist
 * @param backwardRevision whether the backward-revision pass runs after the last segment of the book
 * @param llmSummary whether the rolling summary is written by the model
 * @param chunkCap the most segments in one chunk
 */
public record DialParameters(
        int precedingTargets,
        RepairMode repair,
        int reviewPasses,
        boolean backwardRevision,
        boolean llmSummary,
        int chunkCap)
        implements DialProfile {

    private static final int MANUAL_CHUNK_CAP = 1;

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
            case FAST -> new DialParameters(1, RepairMode.ONE, 0, false, false, 8);
            case BALANCED -> new DialParameters(2, RepairMode.ONE, 1, false, false, 8);
            case MAX -> new DialParameters(3, RepairMode.ONE, 2, true, true, 2);
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

    /**
     * The chunk cap for a review mode: Manual review shows one segment at a time.
     *
     * @param mode the run's review mode; never null
     * @return 1 for {@link ReviewMode#MANUAL}, otherwise this dial's cap
     */
    public int chunkCap(final ReviewMode mode) {
        return Objects.requireNonNull(mode, "mode") == ReviewMode.MANUAL ? MANUAL_CHUNK_CAP : chunkCap;
    }
}
