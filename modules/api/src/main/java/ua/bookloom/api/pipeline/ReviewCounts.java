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
 * @param keptVerbatim accepted segments kept as they are with no model call (numbers, symbols, Roman numerals, a single
 *     character) and not yet reviewed; never in {@code autoAccepted}, and never "kept as source by choice"
 * @param suspicious accepted segments no person has reviewed that the final audit doubts; they stay counted in the
 *     accepted figures too
 * @param sourceFallback the flagged segments with no target and no edit whose formatting marks were lost, so an
 *     export writes them in the source language; a part of {@code flaggedWithoutTarget}
 * @param changedByProofread the segments whose proofread rewrite was kept or waits as a proposal
 * @param waitingOnGender the segments that hold an open gender deferral
 */
public record ReviewCounts(
        int total,
        int autoAccepted,
        int repairedAccepted,
        int flagged,
        int reviewed,
        int pending,
        int sourceKept,
        int flaggedWithoutTarget,
        int keptVerbatim,
        int suspicious,
        int sourceFallback,
        int changedByProofread,
        int waitingOnGender) {

    /** Counts that know nothing of the Proofread stage's changes or of the segments waiting on a gender. */
    public ReviewCounts(
            final int total,
            final int autoAccepted,
            final int repairedAccepted,
            final int flagged,
            final int reviewed,
            final int pending,
            final int sourceKept,
            final int flaggedWithoutTarget,
            final int keptVerbatim,
            final int suspicious,
            final int sourceFallback) {
        this(
                total,
                autoAccepted,
                repairedAccepted,
                flagged,
                reviewed,
                pending,
                sourceKept,
                flaggedWithoutTarget,
                keptVerbatim,
                suspicious,
                sourceFallback,
                0,
                0);
    }

    /**
     * The same counts with the review desk's own lists counted: the desk counts Worth a second look, Changed by
     * proofread and Waiting on a gender by the rules it lists them by.
     *
     * @param secondLook the segments the Worth a second look list holds
     * @param changed the segments the Changed by proofread list holds
     * @param waiting the segments the Waiting on a gender list holds
     * @return a copy with those three counts
     */
    public ReviewCounts withDeskLists(final int secondLook, final int changed, final int waiting) {
        return new ReviewCounts(
                total,
                autoAccepted,
                repairedAccepted,
                flagged,
                reviewed,
                pending,
                sourceKept,
                flaggedWithoutTarget,
                keptVerbatim,
                secondLook,
                sourceFallback,
                changed,
                waiting);
    }

    /** Counts with no suspicious segment and no source-language fallback. */
    public ReviewCounts(
            final int total,
            final int autoAccepted,
            final int repairedAccepted,
            final int flagged,
            final int reviewed,
            final int pending,
            final int sourceKept,
            final int flaggedWithoutTarget,
            final int keptVerbatim) {
        this(
                total,
                autoAccepted,
                repairedAccepted,
                flagged,
                reviewed,
                pending,
                sourceKept,
                flaggedWithoutTarget,
                keptVerbatim,
                0,
                0);
    }

    /** Counts with no source-language fallback. */
    public ReviewCounts(
            final int total,
            final int autoAccepted,
            final int repairedAccepted,
            final int flagged,
            final int reviewed,
            final int pending,
            final int sourceKept,
            final int flaggedWithoutTarget,
            final int keptVerbatim,
            final int suspicious) {
        this(
                total,
                autoAccepted,
                repairedAccepted,
                flagged,
                reviewed,
                pending,
                sourceKept,
                flaggedWithoutTarget,
                keptVerbatim,
                suspicious,
                0);
    }

    /** Rejects a negative count, a {@code flaggedWithoutTarget} above {@code flagged} and a larger fallback. */
    public ReviewCounts {
        if (total < 0
                || autoAccepted < 0
                || repairedAccepted < 0
                || flagged < 0
                || reviewed < 0
                || pending < 0
                || sourceKept < 0
                || flaggedWithoutTarget < 0
                || keptVerbatim < 0
                || suspicious < 0
                || sourceFallback < 0
                || changedByProofread < 0
                || waitingOnGender < 0) {
            throw new IllegalArgumentException("no count may be negative: " + total + ", " + autoAccepted + ", "
                    + repairedAccepted + ", " + flagged + ", " + reviewed + ", " + pending + ", " + sourceKept + ", "
                    + flaggedWithoutTarget + ", " + keptVerbatim + ", " + suspicious + ", " + sourceFallback + ", "
                    + changedByProofread + ", " + waitingOnGender);
        }
        if (flaggedWithoutTarget > flagged) {
            throw new IllegalArgumentException(
                    "flaggedWithoutTarget " + flaggedWithoutTarget + " exceeds flagged " + flagged);
        }
        if (sourceFallback > flaggedWithoutTarget) {
            throw new IllegalArgumentException(
                    "sourceFallback " + sourceFallback + " exceeds flaggedWithoutTarget " + flaggedWithoutTarget);
        }
    }
}
