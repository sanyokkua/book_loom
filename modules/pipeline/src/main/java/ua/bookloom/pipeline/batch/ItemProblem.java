package ua.bookloom.pipeline.batch;

/** What the per-item checks found wrong with a target whose id came back once. */
public enum ItemProblem {

    /** The placeholder tokens are not the source's, in the source's order, or a bracket stands outside a token. */
    TOKENS,

    /** The source's plain-text note markers such as {@code [3]} are not all in the target, in order. */
    MARKERS,

    /** A long target that is the source copied unchanged: the model translated nothing. */
    ECHO,

    /** The target is longer than the language pair's band allows for the source. */
    TOO_LONG,

    /** The target is shorter than the language pair's band allows for the source. */
    TOO_SHORT
}
