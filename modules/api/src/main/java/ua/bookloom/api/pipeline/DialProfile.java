package ua.bookloom.api.pipeline;

/**
 * What one quality-dial setting means for a run, as the window may show it. The pipeline owns the numbers; this view
 * keeps them readable from {@code :ui}, which may not see the pipeline's own types.
 */
public interface DialProfile {

    /**
     * What a segment whose draft fails a check is given.
     *
     * @return the dial's repair mode
     */
    RepairMode repair();

    /**
     * Which decided segments the Proofread stage reads.
     *
     * @return the coverage; {@code NONE} means no model call in the stage
     */
    ProofreadCoverage proofreadCoverage();
}
