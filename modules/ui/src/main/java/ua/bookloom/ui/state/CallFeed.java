package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.JobEvent;

/**
 * Carries the model calls of one piece of blocking work, such as a glossary scan or a setup proposal, onto its busy
 * card: each call snapshot the work announces becomes the card's current call, the one before it the previous one.
 * FX thread only, like the activity it feeds.
 */
@Slf4j
final class CallFeed {

    private final ActivityTracker.Handle handle;
    private final Supplier<Instant> now;
    private final LiveCallState calls = new LiveCallState();

    CallFeed(final ActivityTracker.Handle handle, final Supplier<Instant> now) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.now = Objects.requireNonNull(now, "now");
    }

    /** Shows the event's call when it is a call snapshot and ignores any other event. */
    void accept(final JobEvent event) {
        if (event instanceof CallSnapshotUpdated updated) {
            log.debug(
                    "work call {} {} shown on the busy card",
                    updated.snapshot().callId(),
                    updated.snapshot().state());
            calls.snapshot(updated.snapshot());
            handle.calls(calls.view(now.get()));
        }
    }
}
