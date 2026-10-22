package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;

/**
 * One row of a progress dialog's step list: a step the work announced, or one it planned and has not started.
 *
 * @param kind what the step is
 * @param label the step's short name as the work gave it; empty for a step not started yet
 * @param index the step's position within its kind, from zero
 * @param state whether it runs or has ended, or {@code null} for a planned step that has not started
 * @param itemRef the segment or chapter it is about, or {@code null} when it covers none
 * @param result how it ended, or {@code null} while it has not ended
 * @param text what it read and wrote, in plain words, or {@code null} when the work said nothing of it
 */
public record StepEntry(
        StepKind kind,
        String label,
        int index,
        @Nullable StepState state,
        @Nullable String itemRef,
        @Nullable StepResult result,
        @Nullable StepText text) {

    /** What the pipeline calls the step of the nth brief sample, followed by n (BriefSteps). */
    public static final String SAMPLE_LABEL = "sample-";

    /** Rejects a missing kind or label. */
    public StepEntry {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
    }

    /**
     * The step's identity across its changes: a step started again keeps its row.
     *
     * @return the kind and index, never null
     */
    public String key() {
        return kind.name() + ":" + index;
    }

    /**
     * The number of the brief sample this step reads.
     *
     * @return the sample's number from 1, or empty when the step is not a brief sample
     */
    public OptionalInt sampleNumber() {
        if (!label.startsWith(SAMPLE_LABEL)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(label.substring(SAMPLE_LABEL.length())));
        } catch (final NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Whether the step is planned and has not started.
     *
     * @return {@code true} for a step not announced yet, {@code false} otherwise
     */
    public boolean isPending() {
        return state == null;
    }

    /**
     * Whether the step runs now.
     *
     * @return {@code true} while it has started and not ended, {@code false} otherwise
     */
    public boolean isRunning() {
        return state == StepState.STARTED;
    }

    /**
     * What one step read and wrote, for the dialog's Simple view.
     *
     * @param before the text the step was given, or {@code null}; book text, logged at TRACE only
     * @param after the text it gave back, or {@code null}; book text, logged at TRACE only
     * @param verdict its one-line result, such as {@code kept}, or {@code null}
     */
    public record StepText(
            @Nullable String before,
            @Nullable String after,
            @Nullable String verdict) {}
}
