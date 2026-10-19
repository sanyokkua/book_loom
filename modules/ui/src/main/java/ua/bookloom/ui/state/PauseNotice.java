package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Why a paused run is waiting, in what the provider-error banner names: the code, the segment, the call that failed and
 * how many of its pauses are spent.
 *
 * @param code the code the run paused on, or {@code null} for a pause that is not on an error
 * @param locator the locator of the segment whose step failed, or empty when the pause names none
 * @param kind the kind of the last call that failed, or {@code null} when no call failed before the pause
 * @param pauses how many times the failing step has paused the run, this pause included; zero when not counted
 * @param pausesBeforeFlagging how many pauses the step may cause before its next failure flags it; zero when it is
 *     never flagged for failing
 */
public record PauseNotice(
        @Nullable ErrorCode code, String locator, @Nullable CallKind kind, int pauses, int pausesBeforeFlagging) {

    /** Rejects a missing locator. */
    public PauseNotice {
        Objects.requireNonNull(locator, "locator");
    }

    /**
     * Whether the next failure of the step flags its segment instead of pausing the run again.
     *
     * @return {@code true} if this pause spent the step's last one, {@code false} otherwise
     */
    public boolean isLastPause() {
        return pausesBeforeFlagging > 0 && pauses >= pausesBeforeFlagging;
    }
}
