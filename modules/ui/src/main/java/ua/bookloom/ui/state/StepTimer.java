package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Times the counted step an activity reports through its handle: a step begins when it announces nothing done or its
 * text changes, and the time left is the step's elapsed time per unit finished in it times the units left. Until a
 * step other than the first begins, the first is timed from the activity's start. FX thread only.
 */
final class StepTimer {

    private @Nullable String step;
    private @Nullable Instant stepStart;
    private int stepStartDone;

    /**
     * Notes one report and answers the time left.
     *
     * @param startedAt when the activity began
     * @param done the units finished, from 0
     * @param total the units in the step
     * @param stepText what the step is, already worded
     * @param now the moment of the report
     * @return the time left, or {@code null} while nothing in the step is done or nothing is left
     */
    @Nullable
    Duration eta(final Instant startedAt, final int done, final int total, final String stepText, final Instant now) {
        if (done <= 0 || (step != null && !step.equals(stepText))) {
            stepStart = now;
            stepStartDone = Math.max(0, done);
        }
        step = Objects.requireNonNull(stepText, "stepText");
        final Instant began = stepStart == null ? startedAt : stepStart;
        final int doneInStep = done - stepStartDone;
        return doneInStep <= 0 || total <= done
                ? null
                : Duration.between(began, now).dividedBy(doneInStep).multipliedBy(total - done);
    }
}
