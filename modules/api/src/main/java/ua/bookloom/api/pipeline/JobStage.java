package ua.bookloom.api.pipeline;

/**
 * The stages reported by a translation job.
 */
public enum JobStage {

    /** Segment-by-segment model inference. */
    TRANSLATE,

    /** Same-format checked export. */
    EXPORT
}
