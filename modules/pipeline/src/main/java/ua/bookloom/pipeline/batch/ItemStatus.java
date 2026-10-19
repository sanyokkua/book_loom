package ua.bookloom.pipeline.batch;

/** What a batch reply did with one expected id, so only a failing id falls back to a single-segment draft. */
public enum ItemStatus {

    /** The id came back exactly once with text. */
    OK(true),

    /** The id is absent, or its text is blank. */
    MISSING(false),

    /** The id came back more than once, so no one of its texts can be taken as the answer. */
    DUPLICATE(false),

    /** An id the batch never held; it is no item's answer and signals a model that lost the numbering. */
    EXTRA(false),

    /** The id came back once but its text carries the next item as well, whose own id is missing. */
    MERGED_SUSPECT(false);

    private final boolean usable;

    ItemStatus(final boolean usable) {
        this.usable = usable;
    }

    /** Whether the text under this status may be used, before the per-item checks. */
    public boolean isUsable() {
        return usable;
    }
}
