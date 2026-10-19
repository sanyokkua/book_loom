package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The model request a run is waiting on, as the banner tells it: which call, about which segment, which attempt, how
 * long this attempt has waited against its timeout and how long the call has waited in all.
 *
 * @param kind what the call is for
 * @param locator the locator of the segment the call is about, or empty for a call about none
 * @param moreSegments how many more segments the call is about besides the one named, as a chunk's judge call is
 * @param attempt the attempt, counted from one
 * @param maxAttempts how many attempts the call may make if each one stalls
 * @param attemptWaited how long this attempt has waited, in whole seconds
 * @param timeout how long this attempt may wait, or {@code null} when the model sets no bound
 * @param totalWaited how long the call has waited across its attempts, in whole seconds
 * @param stuck {@code true} once this attempt has waited long enough to offer a way out
 */
public record WaitingCall(
        CallKind kind,
        String locator,
        int moreSegments,
        int attempt,
        int maxAttempts,
        Duration attemptWaited,
        @Nullable Duration timeout,
        Duration totalWaited,
        boolean stuck) {

    /** Rejects a missing part. */
    public WaitingCall {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(attemptWaited, "attemptWaited");
        Objects.requireNonNull(totalWaited, "totalWaited");
    }

    /**
     * The word the catalogue selects the call kind's name by.
     *
     * @return the kind's name in lower case, such as {@code judge} or {@code directed_fix}
     */
    public String kindToken() {
        return tokenOf(kind);
    }

    /**
     * The word the catalogue selects a call kind's name by, shared by the banner and the activity log.
     *
     * @param kind the non-null kind
     * @return the kind's name in lower case
     */
    public static String tokenOf(final CallKind kind) {
        return Objects.requireNonNull(kind, "kind").name().toLowerCase(Locale.ROOT);
    }
}
