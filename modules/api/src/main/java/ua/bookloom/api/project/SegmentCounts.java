package ua.bookloom.api.project;

/**
 * Per-status counts of a project's segments, what {@code SegmentRepository.countsByStatus} answers — a record kept
 * as source by choice is counted only as {@code sourceKept}, never under its own status.
 *
 * @param pending segments not yet translated or judged; never negative
 * @param accepted segments translated and passed QA/judge; never negative
 * @param revised segments reached by an edit or a backward-revision re-render; never negative
 * @param flagged segments that failed after the repair budget was exhausted; never negative
 * @param sourceKept auxiliary segments kept as source by the brief's choice, whatever their status; never negative
 */
public record SegmentCounts(int pending, int accepted, int revised, int flagged, int sourceKept) {

    /**
     * Rejects a negative count in any field.
     */
    public SegmentCounts {
        requireNonNegative(pending, "pending");
        requireNonNegative(accepted, "accepted");
        requireNonNegative(revised, "revised");
        requireNonNegative(flagged, "flagged");
        requireNonNegative(sourceKept, "sourceKept");
    }

    private static void requireNonNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, but was " + value);
        }
    }
}
