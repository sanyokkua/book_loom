package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Where an export has got to, announced from the thread that runs it.
 *
 * @param step what the export is doing now
 * @param done how many units of that step are finished
 * @param total how many units the step has in all, so a bar can be determinate; at least {@code done}
 */
public record ExportProgress(Step step, int done, int total) {

    /** The stages an export passes through, in order. */
    public enum Step {
        /** Checking the destination, the side files and the stored project before anything slow. */
        VALIDATING,
        /** One model call per segment whose character's gender became known, to re-render it. */
        CONSISTENCY_RETRY,
        /** One model call per repaired or flagged paragraph, checking it against its neighbours. */
        CONSISTENCY_NEIGHBOUR,
        /** Writing the book and the chosen side files. */
        WRITING
    }

    /** Rejects a missing step and counts that cannot be a progress. */
    public ExportProgress {
        Objects.requireNonNull(step, "step");
        if (done < 0 || total < done) {
            throw new IllegalArgumentException("done " + done + " must lie within 0.." + total);
        }
    }
}
