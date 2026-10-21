package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.Objects;

/**
 * The figures as a plain count of steps: the share the work has reached, which never falls, and no time left, since a
 * plain count cannot tell a quick check from a slow draft.
 */
public final class StepCountFigures implements ProgressFigures {

    private static final int PERCENT = 100;

    @Override
    public Figures of(final StepLog steps, final Instant now) {
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(now, "now");
        final int total = steps.total();
        return new Figures((int) Math.floor(steps.reached() * PERCENT), Math.min(steps.done(), total), total, null);
    }
}
