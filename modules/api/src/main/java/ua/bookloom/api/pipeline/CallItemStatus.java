package ua.bookloom.api.pipeline;

/** What a batch reply did with one expected id; a screen words each in its own language. */
public enum CallItemStatus {
    /** The id came back exactly once with text. */
    OK,
    /** The id is absent, or its text is blank. */
    MISSING,
    /** The id came back more than once. */
    DUPLICATE,
    /** An id the batch never held. */
    EXTRA,
    /** The id came back once but its text carries the next item as well. */
    MERGED_SUSPECT
}
