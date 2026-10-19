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
    TOO_SHORT,

    /** The target has fewer sentences than the source's significant ones: a sentence was dropped. */
    SENTENCES,

    /** The target carries protocol text of the reply — a {@code terms} object, an id entry, a code fence. */
    LEAKED,

    /** The target holds control characters, such as a model's raw quote marks written as U+001C-U+001F. */
    CONTROL_CHARACTERS,

    /**
     * The target's quote marks do not pair up while the source's do: a closing mark with no opening one at the start
     * of an item is the tail of the neighbour's sentence, which the model moved across the id.
     */
    QUOTES
}
