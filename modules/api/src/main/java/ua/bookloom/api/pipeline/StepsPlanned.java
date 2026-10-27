package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * The steps of one kind that join the run's total: the plan when a chunk is entered, or work added later.
 *
 * @param kind what the steps are
 * @param added how many steps join the total; not negative
 * @param estimated {@code true} when {@code added} is a forecast that the run may correct, {@code false} when exact
 */
public record StepsPlanned(StepKind kind, int added, boolean estimated) implements JobEvent {

    /** Rejects a missing kind and a negative count. */
    public StepsPlanned {
        Objects.requireNonNull(kind, "kind");
        if (added < 0) {
            throw new IllegalArgumentException("added must not be negative: " + added);
        }
    }
}
