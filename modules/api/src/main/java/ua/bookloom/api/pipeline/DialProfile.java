package ua.bookloom.api.pipeline;

/**
 * What one quality-dial setting means for a run, as the window may show it. The pipeline owns the numbers; this view
 * keeps them readable from {@code :ui}, which may not see the pipeline's own types.
 */
public interface DialProfile {

    /**
     * How many accepted preceding targets accompany a draft.
     *
     * @return the count, zero or more
     */
    int precedingTargets();

    /**
     * How many directed-fix rounds a flagged segment gets.
     *
     * @return the count, zero or more
     */
    int repairRounds();

    /**
     * How many times the reviewer reads each chunk.
     *
     * @return the count; zero means no reviewer
     */
    int reviewPasses();

    /**
     * Whether the backward-revision pass runs after the last segment.
     *
     * @return {@code true} when it runs
     */
    boolean backwardRevision();

    /**
     * Whether the model writes the rolling summary.
     *
     * @return {@code true} when it does
     */
    boolean llmSummary();

    /**
     * The most segments in one request.
     *
     * @return the cap, at least one
     */
    int chunkCap();
}
