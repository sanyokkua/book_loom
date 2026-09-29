package ua.bookloom.ui.state;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Decides when the banner should say the model is slow to answer, and how long it has been.
 *
 * <p>Not thread-safe: {@link RunSession} calls it under its publish lock.
 */
@Slf4j
final class WaitNotice {

    /** A request answered within this many seconds is normal for a local model and is not worth a notice. */
    private static final long THRESHOLD_SECONDS = 10;

    private final StateMirror mirror;
    private final Clock clock;
    private @Nullable Instant callStartedAt;
    private int shownSeconds = StateMirror.NOT_WAITING;

    WaitNotice(final StateMirror mirror, final Clock clock) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Restarts the clock for a request just sent. */
    void callStarted() {
        clear("a new request started");
        callStartedAt = clock.instant();
    }

    /** Both clocks stop with the request: a decision, a pause or the end of the run means nothing is outstanding. */
    void clear(final String why) {
        callStartedAt = null;
        if (shownSeconds != StateMirror.NOT_WAITING) {
            log.debug("waiting notice cleared after {} s because {}", shownSeconds, why);
            shownSeconds = StateMirror.NOT_WAITING;
            mirror.publishWaitingSeconds(StateMirror.NOT_WAITING);
        }
    }

    /** Publishes the seconds the outstanding request has waited, once they pass the threshold and change. */
    void publish() {
        final Instant started = callStartedAt;
        if (started == null) {
            return;
        }
        final long waited = Duration.between(started, clock.instant()).toSeconds();
        if (waited < THRESHOLD_SECONDS || waited == shownSeconds) {
            return;
        }
        if (shownSeconds == StateMirror.NOT_WAITING) {
            log.debug("a model request has been outstanding for {} s, showing the waiting notice", waited);
        }
        shownSeconds = Math.toIntExact(waited);
        mirror.publishWaitingSeconds(shownSeconds);
    }
}
