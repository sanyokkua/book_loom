package ua.bookloom.api.pipeline;

/**
 * The Book Brief's speed/quality trade-off, controlling how much repair and judging effort a run spends per
 * segment ({@code specs/book-brief/spec.md} "Choose the quality dial and show what it turns on").
 */
public enum QualityDial {

    /** No reviewer; fastest wall-clock time. */
    FAST,

    /** The default balance of speed and repair/review effort. */
    BALANCED,

    /** A second reviewer pass and the backward-revision pass. */
    MAX
}
