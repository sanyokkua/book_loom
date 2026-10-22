package ua.bookloom.ui.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Which run states let another book be opened: the run has ended, or at least no longer runs. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RunStates {

    /** Whether the run has ended, so its project can be deleted. */
    static boolean hasEnded(final RunState state) {
        return switch (state) {
            case IDLE, STOPPED, COMPLETED, FAILED -> true;
            case RUNNING, PAUSING, PAUSED, STOPPING -> false;
        };
    }

    /** Whether the run has ended or sits paused, so another project can be opened beside it. */
    static boolean isSettled(final RunState state) {
        return hasEnded(state) || state == RunState.PAUSED;
    }
}
