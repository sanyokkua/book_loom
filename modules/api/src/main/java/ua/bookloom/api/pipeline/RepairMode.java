package ua.bookloom.api.pipeline;

/**
 * What a quality dial gives a segment whose draft keeps a blocking or repair-level finding after the code repairs.
 * The dial never depends on the model: Balanced and Max repair the same way.
 */
public enum RepairMode {

    /** No fixing call: only a draft whose formatting marks stay lost after the code restore is drafted once more. */
    MARKS_ONLY,

    /** One repair call naming the defects; what stays wrong is marked for the Proofread stage. */
    ONE
}
