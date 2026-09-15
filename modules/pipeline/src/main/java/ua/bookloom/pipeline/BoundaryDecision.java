package ua.bookloom.pipeline;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.PauseReason;

/** A control decision made at one safe job boundary. */
record BoundaryDecision(boolean cancelled, @Nullable PauseReason pauseReason) {

    static BoundaryDecision continueRunning() {
        return new BoundaryDecision(false, null);
    }

    static BoundaryDecision cancel() {
        return new BoundaryDecision(true, null);
    }

    static BoundaryDecision pause(final PauseReason reason) {
        return new BoundaryDecision(false, reason);
    }
}
