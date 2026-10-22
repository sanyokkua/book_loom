package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What an activity had shown when it ended, kept for its results: the steps it announced, how long it ran and whether
 * its dialog was in the background.
 *
 * @param steps the steps it announced
 * @param elapsed how long it ran by the tracker's clock
 * @param hidden whether its dialog was in the background when it ended
 */
record RunEnd(StepLog steps, Duration elapsed, boolean hidden) {

    /** An activity that left nothing to keep. */
    static final RunEnd NONE = new RunEnd(StepLog.EMPTY, Duration.ZERO, false);

    RunEnd {
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(elapsed, "elapsed");
    }

    /**
     * Reads what a registered activity shows now.
     *
     * @param activities the tracker that holds it
     * @param id the registration's id
     * @return its steps, age and visibility, or {@link #NONE} when it is not registered
     */
    static RunEnd of(final ActivityTracker activities, final long id) {
        final Optional<Activity> shown = activities.running().stream()
                .filter(activity -> activity.id() == id)
                .findFirst();
        return shown.map(activity -> new RunEnd(
                        activity.steps(),
                        Duration.between(activity.startedAt(), activities.now()),
                        activity.isHidden()))
                .orElse(NONE);
    }

    /**
     * The same, for a handle that may already be gone.
     *
     * @param activities the tracker
     * @param handle the registration, or {@code null}
     * @return what it showed, or {@link #NONE}
     */
    static RunEnd of(final ActivityTracker activities, final ActivityTracker.@Nullable Handle handle) {
        return handle == null ? NONE : of(activities, handle.id());
    }
}
