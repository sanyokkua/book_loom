package ua.bookloom.api.pipeline;

/**
 * The Book Brief's speed/quality trade-off, controlling how much repair and judging effort a run spends per
 * segment ({@code specs/book-brief/spec.md} "Choose the quality dial and show what it turns on").
 */
public enum QualityDial {

    /** Fewest repair rounds and judge calls; fastest wall-clock time. */
    FAST,

    /** The default balance of speed and repair/judge effort. */
    BALANCED,

    /** Every available repair round and judge/backward-revision pass. */
    MAX
}
