package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;

/**
 * Everything the run-state table reads, gathered in one place so the table itself is a pure function of it.
 *
 * @param state where the run is
 * @param notice what a start or its preparation was refused with, or {@code null}
 * @param recovery the wait for the AI program after an error, or {@code null} when none runs or ran
 * @param pausedOn the code of the error the run is paused on, or {@code null}
 * @param waiting the call the run waits on, or {@code null} while no call is overdue
 * @param percent the share of the work done, 0 to 100
 * @param timeLeft the time left already worded ("about 1 h 10 min"), or {@code null} while it is not known
 * @param decided the segments decided so far; at least zero
 * @param total the book's segments; at least zero
 * @param toReview the segments that need the person's review; at least zero
 * @param inFlight the segments of the call in flight, which Skip and Stop name; at least zero
 * @param model the AI program and model chosen
 * @param controls which run controls the view model offers, the one command gate
 * @param otherWork other model work that holds start and resume, or {@code null}
 * @param span the segments of the current call as worded for a person ("ch2 · p05–ch2 · p08"), or {@code null}
 * @param continuesAt where a stopped or reopened project continues, or {@code null} while not known
 */
public record RunStateInputs(
        RunState state,
        @Nullable RunNotice notice,
        @Nullable RecoveryState recovery,
        @Nullable ErrorCode pausedOn,
        @Nullable WaitingCall waiting,
        int percent,
        @Nullable String timeLeft,
        int decided,
        int total,
        int toReview,
        int inFlight,
        ModelChoice model,
        Controls controls,
        @Nullable ActivityKind otherWork,
        @Nullable String span,
        @Nullable String continuesAt) {

    /** Rejects a missing part or a negative count. */
    public RunStateInputs {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(controls, "controls");
        if (decided < 0 || total < 0 || toReview < 0 || inFlight < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }
}
