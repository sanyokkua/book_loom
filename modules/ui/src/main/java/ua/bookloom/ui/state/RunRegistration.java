package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Registers the translation run with the {@link ActivityTracker} from the mirror's run state: it counts while the run
 * translates (running, pausing, stopping) or waits for the provider by itself, not while it waits paused for the
 * person, stopped or ended. FX thread only.
 */
@Slf4j
final class RunRegistration {

    private final ActivityTracker tracker;
    private ActivityTracker.@Nullable Handle translation;

    RunRegistration(final ActivityTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    /** Follows the run from now on, and registers it at once when it already translates. */
    void follow(final StateMirror mirror) {
        mirror.runState().addListener((observed, was, now) -> follow(isTranslating(mirror)));
        // The countdown republishes the recovery every second; only a change of whether it waits matters here.
        mirror.review().recovery().addListener((observed, was, now) -> {
            if (RecoveryState.waits(was) != RecoveryState.waits(now)) {
                follow(isTranslating(mirror));
            }
        });
        follow(isTranslating(mirror));
    }

    /**
     * Whether the run is at work: translating, or paused on a provider error that it recovers from by itself, which is
     * still the run's own activity and keeps the machine awake.
     *
     * @param mirror where the run's state and recovery are read; on the FX thread
     * @return {@code true} while the run translates or waits for the provider by itself, {@code false} otherwise
     */
    static boolean isTranslating(final StateMirror mirror) {
        final RecoveryState recovery = mirror.review().recovery().get();
        return switch (mirror.runState().get()) {
            case RUNNING, PAUSING, STOPPING -> true;
            case PAUSED -> RecoveryState.waits(recovery);
            case IDLE, STOPPED, COMPLETED, FAILED -> false;
        };
    }

    private void follow(final boolean translating) {
        final ActivityTracker.Handle registered = translation;
        if (translating && registered == null) {
            translation = tracker.begin(ActivityKind.TRANSLATION, null);
        } else if (!translating && registered != null) {
            registered.end();
            translation = null;
        } else {
            log.debug("the translation activity stays {}", translating ? "registered" : "absent");
        }
    }
}
