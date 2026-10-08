package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces a model call's snapshot as it now stands: once when its first attempt goes out, again for every later
 * attempt and at its end, and once more for each outcome noted after its reply was read. Every announcement of one
 * call carries the same {@link CallSnapshot#callId()}, so a screen replaces what it shows rather than adding to it.
 *
 * <p>The snapshot carries book text and the model's reply; a listener keeps them in memory and never logs them above
 * TRACE.
 *
 * @param snapshot the call as it stands
 */
public record CallSnapshotUpdated(CallSnapshot snapshot) implements JobEvent {

    /** Rejects an event without a snapshot. */
    public CallSnapshotUpdated {
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
