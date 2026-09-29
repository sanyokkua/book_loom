package ua.bookloom.api.pipeline;

/**
 * The review queue's segment counts, feeding the review panel's tiles and the Export screen's pre-export summary.
 *
 * @param total every segment in the project
 * @param autoAccepted segments accepted without repair
 * @param repairedAccepted segments accepted after repair
 * @param flagged segments currently flagged
 * @param reviewed segments a person has acted on
 * @param pending segments not yet decided
 * @param sourceKept auxiliary segments kept as source by choice
 * @param flaggedWithoutTarget the flagged segments that hold no machine target, still counted in {@code flagged} —
 *     an export writes them in the source language
 */
public record ReviewCounts(
        int total,
        int autoAccepted,
        int repairedAccepted,
        int flagged,
        int reviewed,
        int pending,
        int sourceKept,
        int flaggedWithoutTarget) {

    /** Rejects a negative count, and a {@code flaggedWithoutTarget} above {@code flagged}. */
    public ReviewCounts {
        if (total < 0
                || autoAccepted < 0
                || repairedAccepted < 0
                || flagged < 0
                || reviewed < 0
                || pending < 0
                || sourceKept < 0
                || flaggedWithoutTarget < 0) {
            throw new IllegalArgumentException("no count may be negative: " + total + ", " + autoAccepted + ", "
                    + repairedAccepted + ", " + flagged + ", " + reviewed + ", " + pending + ", " + sourceKept + ", "
                    + flaggedWithoutTarget);
        }
        if (flaggedWithoutTarget > flagged) {
            throw new IllegalArgumentException(
                    "flaggedWithoutTarget " + flaggedWithoutTarget + " exceeds flagged " + flagged);
        }
    }
}
