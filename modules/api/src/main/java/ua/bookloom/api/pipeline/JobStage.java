package ua.bookloom.api.pipeline;

/**
 * The stages reported by a translation job. Writing the book is not one of them: export is a separate job.
 */
public enum JobStage {

    /** Style-sheet preparation before any segment is drafted. */
    PREP,

    /** Segment-by-segment model inference. */
    TRANSLATE,

    /** The backward-revision pass over deferred segments. */
    REVISE
}
