package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * What the final consistency pass did during one export, so the screen can say something after a pass that changes
 * nothing visible.
 *
 * @param status whether the pass ran, and whether it had a model for its gender step
 * @param termSubstitutions segments whose locked term was swept to its new rendering
 * @param genderReRenders segments re-rendered because a character's gender became known
 */
public record ConsistencySummary(Status status, int termSubstitutions, int genderReRenders) {

    /** The summary of an export whose pass was switched off: nothing ran, nothing is to be said. */
    public static final ConsistencySummary NOT_RUN = new ConsistencySummary(Status.NOT_RUN, 0, 0);

    /** How far the pass got. */
    public enum Status {
        /** The switch was off. */
        NOT_RUN,
        /** The pass ran with the chosen model, so both of its steps ran. */
        RAN,
        /** The pass ran without a model, so only the name sweep ran and gender deferrals stayed open. */
        RAN_WITHOUT_MODEL
    }

    /** Rejects a missing status or a negative count. */
    public ConsistencySummary {
        Objects.requireNonNull(status, "status");
        if (termSubstitutions < 0 || genderReRenders < 0) {
            throw new IllegalArgumentException(
                    "no count may be negative: " + termSubstitutions + ", " + genderReRenders);
        }
    }

    /** Segments the pass changed. */
    public int adjusted() {
        return termSubstitutions + genderReRenders;
    }
}
