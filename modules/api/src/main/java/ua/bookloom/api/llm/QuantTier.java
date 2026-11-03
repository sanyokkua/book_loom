package ua.bookloom.api.llm;

/** How well a quant type keeps translation quality for one size class, as the bundled knowledge file states it. */
public enum QuantTier {
    /** Indistinguishable from the full-precision model in the measurements. */
    EXCELLENT,
    /** A small, usually unnoticeable loss. */
    GOOD,
    /** A visible loss that translation still tolerates. */
    ACCEPTABLE,
    /** A loss that shows in the text; worth a warning. */
    POOR,
    /** Quality collapses; not for translating a book. */
    UNUSABLE,
    /** No measurement exists yet. */
    UNMEASURED,
    /** A type llama.cpp no longer reads. */
    REMOVED
}
