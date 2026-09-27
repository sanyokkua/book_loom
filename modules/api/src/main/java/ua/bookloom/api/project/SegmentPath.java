package ua.bookloom.api.project;

/**
 * How a stored segment reached its current target text
 * ({@code specs/review-queue/spec.md} "Move segments only through the segment status machine").
 */
public enum SegmentPath {

    /** A fresh model draft that passed every hard gate. */
    DRAFT,

    /** Reused from translation memory without a model call. */
    TM_REUSE,

    /** A draft repaired after failing a hard gate. */
    REPAIRED,

    /** The person's own edit. */
    USER,

    /** Never stored; review views report it for a record of the auxiliary unit kept as source by choice. */
    SOURCE_KEPT
}
