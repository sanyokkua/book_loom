package ua.bookloom.pipeline.dial;

import java.util.Objects;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * What one quality-dial setting means for a run's mechanics.
 *
 * @param precedingTargets how many accepted preceding targets accompany a draft
 * @param repairRounds how many directed-fix rounds a flagged segment gets
 * @param judge whether the judge reads each chunk
 * @param backwardRevision whether a revision pass runs at the end of a unit
 * @param llmSummary whether the rolling summary is written by the model
 * @param chunkCap the most segments in one chunk
 */
public record DialParameters(
        int precedingTargets,
        int repairRounds,
        boolean judge,
        boolean backwardRevision,
        boolean llmSummary,
        int chunkCap) {

    private static final int MANUAL_CHUNK_CAP = 1;

    /**
     * Looks the dial's row up.
     *
     * @param dial the selected dial; never null
     * @return the row for {@code dial}
     */
    public static DialParameters of(final QualityDial dial) {
        return switch (Objects.requireNonNull(dial, "dial")) {
            case FAST -> new DialParameters(1, 1, false, false, false, 8);
            case BALANCED -> new DialParameters(2, 2, true, false, false, 4);
            case MAX -> new DialParameters(3, 3, true, true, true, 2);
        };
    }

    /**
     * This row with no repair round: a review retry is one attempt the person asked for, decided by the same checks
     * and judge, never followed by an automatic repair.
     *
     * @return the same row with {@code repairRounds} 0
     */
    public DialParameters withoutRepairRounds() {
        return new DialParameters(precedingTargets, 0, judge, backwardRevision, llmSummary, chunkCap);
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
