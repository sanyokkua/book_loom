package ua.bookloom.ui.state;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * What stops each registered activity from outside, and the one rule of stopping: an activity that holds its answer for
 * the person has no work left to interrupt, so stopping it drops the answer instead. FX thread only.
 */
@Slf4j
final class ActivityStops {

    private final Map<Long, Runnable> cancels = new HashMap<>();

    void register(final long id, final Runnable cancel) {
        cancels.put(id, cancel);
    }

    void forget(final long id) {
        cancels.remove(id);
    }

    /**
     * Stops one activity.
     *
     * @param activity the activity as it is now
     * @param markCancelling what shows that it was asked to stop, run before the work is interrupted
     */
    void stop(final Activity activity, final Runnable markCancelling) {
        final @Nullable ResultOffer held = activity.outcome().offer();
        if (held != null) {
            log.info(
                    "activity {} #{} stopped while its answer was held: the answer is dropped",
                    activity.kind(),
                    activity.id());
            held.discard().run();
            return;
        }
        final Runnable cancel = cancels.get(activity.id());
        if (cancel == null) {
            log.debug("activity {} #{} cannot be stopped from outside", activity.kind(), activity.id());
            return;
        }
        if (activity.cancelling()) {
            log.debug("activity {} #{} is already being stopped", activity.kind(), activity.id());
            return;
        }
        markCancelling.run();
        log.info("activity {} #{} asked to stop", activity.kind(), activity.id());
        cancel.run();
    }
}
