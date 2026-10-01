package ua.bookloom.ui.state;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;

/**
 * Decides when the banner should say the model is slow to answer, and what it is waiting on.
 *
 * <p>Each attempt has its own clock, restarted by its start announcement, and the call keeps a second clock from its
 * first attempt, so a retried call reads "attempt 2 of 2 · 0:12 of 1:30 · 1:42 in all" rather than one clock counting
 * across both. Not thread-safe: {@link RunSession} calls it under its publish lock.
 */
@Slf4j
final class WaitNotice {

    /** A request answered within this many seconds is normal for a local model and is not worth a notice. */
    private static final long THRESHOLD_SECONDS = 10;

    /** An attempt outstanding this long is offered a way out: skip the segment, send it again or pause. */
    static final long STUCK_SECONDS = 60;

    /** The call being waited on, as its latest attempt announced it. */
    private record Outstanding(ModelCallStarted call, String locator, Instant attemptAt, Instant callAt) {}

    private final StateMirror mirror;
    private final Clock clock;
    private @Nullable Outstanding outstanding;
    private int shownSeconds = StateMirror.NOT_WAITING;

    WaitNotice(final StateMirror mirror, final Clock clock) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Restarts the attempt's clock, and the call's clock too for a first attempt.
     *
     * @param started the attempt's announcement
     * @param locator the locator of the segment it is about, or empty for none
     */
    void callStarted(final ModelCallStarted started, final String locator) {
        final Instant now = clock.instant();
        final Outstanding before = outstanding;
        final boolean sameCall =
                started.attempt() > 1 && before != null && before.call().kind() == started.kind();
        clear("a new request started");
        outstanding = new Outstanding(started, locator, now, sameCall && before != null ? before.callAt() : now);
        log.debug(
                "waiting on {} for {} attempt {} of {} timeout {}",
                started.kind(),
                locator,
                started.attempt(),
                started.maxAttempts(),
                started.timeout());
    }

    /**
     * An attempt ended: an answer ends the wait, a failure leaves it for the next attempt or the pause that follows.
     *
     * @param finished the attempt's end
     */
    void callFinished(final ModelCallFinished finished) {
        if (finished.isAnswered()) {
            clear("the model answered");
        }
    }

    /** Both clocks stop with the request: a decision, a pause or the end of the run means nothing is outstanding. */
    void clear(final String why) {
        outstanding = null;
        if (shownSeconds != StateMirror.NOT_WAITING) {
            log.debug("waiting notice cleared after {} s because {}", shownSeconds, why);
            shownSeconds = StateMirror.NOT_WAITING;
            mirror.live().publishWaitingCall(null);
            mirror.publishWaitingSeconds(StateMirror.NOT_WAITING);
        }
    }

    /** Publishes what the outstanding request has waited, once it passes the threshold and changes. */
    void publish() {
        final Outstanding waiting = outstanding;
        if (waiting == null) {
            return;
        }
        final Instant now = clock.instant();
        final long waited = Duration.between(waiting.attemptAt(), now).toSeconds();
        if (waited < THRESHOLD_SECONDS || waited == shownSeconds) {
            return;
        }
        if (shownSeconds == StateMirror.NOT_WAITING) {
            log.debug("a model request has been outstanding for {} s, showing the waiting notice", waited);
        }
        if (waited >= STUCK_SECONDS && shownSeconds < STUCK_SECONDS) {
            log.warn(
                    "a {} request has been outstanding for {} s; offering skip, retry and pause",
                    waiting.call().kind(),
                    waited);
        }
        shownSeconds = Math.toIntExact(waited);
        // The call first: the screen redraws on the seconds and reads the call it names.
        mirror.live().publishWaitingCall(callOf(waiting, now, waited));
        mirror.publishWaitingSeconds(shownSeconds);
    }

    private static WaitingCall callOf(final Outstanding waiting, final Instant now, final long waited) {
        final ModelCallStarted call = waiting.call();
        final int named = call.segmentIds().isEmpty() ? 0 : 1;
        return new WaitingCall(
                call.kind(),
                waiting.locator(),
                call.segmentIds().size() - named,
                call.attempt(),
                call.maxAttempts(),
                Duration.ofSeconds(waited),
                call.timeout(),
                Duration.ofSeconds(Duration.between(waiting.callAt(), now).toSeconds()),
                waited >= STUCK_SECONDS);
    }
}
