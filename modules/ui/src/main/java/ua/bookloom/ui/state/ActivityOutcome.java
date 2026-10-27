package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What a short piece of work has to show once it has an answer, next to its steps: the decided rows and the offer that
 * takes or drops them.
 *
 * @param decisions the rows of "What it decides"; never null, empty for work with none
 * @param offer the held offer, or {@code null} while there is none
 */
public record ActivityOutcome(
        List<DecisionRow> decisions, @Nullable ResultOffer offer) {

    /** Nothing decided, nothing offered. */
    public static final ActivityOutcome NONE = new ActivityOutcome(List.of(), null);

    /** Copies the rows. */
    public ActivityOutcome {
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
    }

    ActivityOutcome withDecisions(final List<DecisionRow> rows) {
        return new ActivityOutcome(rows, offer);
    }

    ActivityOutcome withOffer(final @Nullable ResultOffer held) {
        return new ActivityOutcome(decisions, held);
    }
}
