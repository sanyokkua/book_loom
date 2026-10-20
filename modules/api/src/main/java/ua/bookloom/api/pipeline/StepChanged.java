package ua.bookloom.api.pipeline;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One step started or ended.
 *
 * @param kind what the step is
 * @param label the step's short name for a progress line
 * @param index the step's position within its kind, from zero
 * @param state whether it started or ended
 * @param itemRef the segment id or chapter the step is about, or null when it covers none
 * @param result how it ended; present only when {@code state} is {@link StepState#ENDED}
 */
public record StepChanged(
        StepKind kind,
        String label,
        int index,
        StepState state,
        @Nullable String itemRef,
        @Nullable StepResult result) implements JobEvent {

    /** Rejects a missing part, a negative index, and a result on a step that has not ended. */
    public StepChanged {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(state, "state");
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative: " + index);
        }
        if (state == StepState.STARTED && result != null) {
            throw new IllegalArgumentException("a started step has no result: " + result);
        }
    }
}
