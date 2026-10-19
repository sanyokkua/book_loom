package ua.bookloom.pipeline.revision;

/**
 * Which model steps a consistency pass runs besides the name sweep and the gender re-render.
 *
 * @param reviseDoubted whether every flagged or audit-doubted segment is drafted again first, kept only when better,
 *     and then checked against its neighbours too
 * @param everySegment whether the check against the neighbours reads every machine-owned paragraph, not only the
 *     repaired, flagged and doubted ones
 */
public record PassOptions(boolean reviseDoubted, boolean everySegment) {

    /** The Max run's own backward revision: the neighbour check of the repaired and flagged paragraphs only. */
    public static final PassOptions BACKWARD_REVISION = new PassOptions(false, false);
}
