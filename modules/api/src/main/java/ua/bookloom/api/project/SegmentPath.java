package ua.bookloom.api.project;

/**
 * How a stored segment reached its current target text
 * ({@code specs/review-queue/spec.md} "Move segments only through the segment status machine").
 */
public enum SegmentPath {

    /** Decided with no self-heal round: an accepted fresh draft, or a segment flagged at once with none run. */
    DRAFT,

    /** Reused from translation memory without a model call. */
    TM_REUSE,

    /** Decided after at least one self-heal round, whether the outcome was accepted or flagged. */
    REPAIRED,

    /** The person's own edit. */
    USER,

    /** Never stored; review views report it for a record of the auxiliary unit kept as source by choice. */
    SOURCE_KEPT
}
