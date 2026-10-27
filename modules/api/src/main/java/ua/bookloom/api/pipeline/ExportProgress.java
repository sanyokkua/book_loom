package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where an export has got to, announced from the thread that runs it.
 *
 * @param step what the export is doing now
 * @param done how many units of that step are finished
 * @param total how many units the step has in all, so a bar can be determinate; at least {@code done}
 * @param itemRef the segment the step just handled, or null when the announcement covers none
 * @param before the segment's text before the step, or null; book text, so a listener never logs it above TRACE
 * @param after the segment's text after the step, or null; book text, same rule
 * @param verdict the step's verdict on the segment (for example kept or dropped), or null
 * @param neighbours the paragraphs around a reread paragraph, or null when the step reads none; book text, same rule
 */
public record ExportProgress(
        Step step,
        int done,
        int total,
        @Nullable String itemRef,
        @Nullable String before,
        @Nullable String after,
        @Nullable String verdict,
        @Nullable Neighbours neighbours) {

    /**
     * An announcement about one segment with no neighbours to show.
     *
     * @param step what the export is doing now
     * @param done how many units of that step are finished
     * @param total how many units the step has in all; at least {@code done}
     * @param itemRef the segment handled, or null
     * @param before the text before the step, or null; book text
     * @param after the text after the step, or null; book text
     * @param verdict the step's verdict, or null
     */
    public ExportProgress(
            final Step step,
            final int done,
            final int total,
            final @Nullable String itemRef,
            final @Nullable String before,
            final @Nullable String after,
            final @Nullable String verdict) {
        this(step, done, total, itemRef, before, after, verdict, null);
    }

    /**
     * An announcement that covers no single segment.
     *
     * @param step what the export is doing now
     * @param done how many units of that step are finished
     * @param total how many units the step has in all; at least {@code done}
     */
    public ExportProgress(final Step step, final int done, final int total) {
        this(step, done, total, null, null, null, null, null);
    }

    /**
     * A reread paragraph with the decided paragraphs around it, so it can be read in its place: source on one side,
     * translation on the other. Every text is book text, logged at TRACE only.
     *
     * @param previousSource the paragraph before it, or null at the start of a document
     * @param previousTarget that paragraph's translation, or null when it has none
     * @param source this paragraph's source text
     * @param nextSource the paragraph after it, or null at the end of a document or when it is not decided yet
     * @param nextTarget that paragraph's translation, or null when it has none
     */
    public record Neighbours(
            @Nullable String previousSource,
            @Nullable String previousTarget,
            String source,
            @Nullable String nextSource,
            @Nullable String nextTarget) {

        /** Rejects a missing source. */
        public Neighbours {
            Objects.requireNonNull(source, "source");
        }
    }

    /** The stages an export passes through, in order. */
    public enum Step {
        /** Checking the destination, the side files and the stored project before anything slow. */
        VALIDATING,
        /** One model call per segment whose character's gender became known, to re-render it. */
        CONSISTENCY_RETRY,
        /** One fresh draft per flagged or audit-doubted segment, kept only when it is better; counted by segment. */
        RETRY_DOUBTED,
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
