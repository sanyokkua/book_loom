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

    /**
     * Accepted as its own source with no model call, because a reader of the target language reads it unchanged — a
     * chapter number, a scene break, a Roman numeral, a single character, or only a locked name, which is written as
     * its locked rendering. Counted apart from the accepted translations and never written to translation memory.
     */
    VERBATIM,

    /** Never stored; review views report it for a record of the auxiliary unit kept as source by choice. */
    SOURCE_KEPT
}
