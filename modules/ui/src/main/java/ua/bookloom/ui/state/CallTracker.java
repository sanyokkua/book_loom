package ua.bookloom.ui.state;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RecoveryWaiting;

/**
 * What a run's model calls tell the screen: the request it waits on, how the server has been answering, and which call
 * a pause on an error was about.
 *
 * <p>Kept apart from {@link RunSession} so that class stays a readable size. Not thread-safe: the session calls it under
 * its publish lock.
 */
@Slf4j
final class CallTracker {

    private final StateMirror mirror;
    private final Clock clock;
    private final ActivityLogFeed feed;
    private final WaitNotice wait;
    private final ConnectionHealth health = new ConnectionHealth();
    private @Nullable CallKind lastFailed;
    private ConnectionStatus shownStatus = ConnectionStatus.UNKNOWN;
    private @Nullable RecoveryState recovery;

    CallTracker(final StateMirror mirror, final Clock clock, final ActivityLogFeed feed) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.feed = Objects.requireNonNull(feed, "feed");
        this.wait = new WaitNotice(mirror, clock);
    }

    void started(final ModelCallStarted started) {
        wait.callStarted(started, feed.callLocator(started.segmentIds()));
    }

    /**
     * Counts an attempt's end.
     *
     * @param finished the attempt's end
     * @return the activity-log line it is reported by
     */
    LogEntry finished(final ModelCallFinished finished) {
        wait.callFinished(finished);
        health.finished(finished, clock.instant());
        if (!finished.isAnswered() && finished.failure() != ErrorCode.cancelled) {
            lastFailed = finished.kind();
        }
        return feed.modelCall(finished);
    }

    void clearWait(final String why) {
        wait.clear(why);
    }

    /**
     * One cadence period: the wait's clocks and the connection status, each published only when it moved.
     *
     * @param draftTokensPerSecond the run's drafting speed, or {@code null} before one is known
     */
    void publish(final @Nullable Double draftTokensPerSecond) {
        wait.publish();
        publishCountdown();
        final ConnectionStatus status = health.snapshot(clock.instant(), draftTokensPerSecond);
        if (!status.equals(shownStatus)) {
            shownStatus = status;
            mirror.live().publishConnection(status);
        }
    }

    /**
     * Follows the engine's automatic recovery: publishes where it stands and words its activity-log line.
     *
     * @param waiting the engine's announcement
     * @return the activity-log line it is reported by
     */
    LogEntry recovery(final RecoveryWaiting waiting) {
        final RecoveryState state = RecoveryState.of(waiting, clock.instant(), clock.getZone());
        log.debug(
                "recovery {} attempt {} next in {} s, last probe {}",
                state.status(),
                state.attempt(),
                state.secondsLeft(),
                state.probeFailure());
        recovery = state;
        mirror.review().publishRecovery(state);
        final Instant next = waiting.nextTryAt();
        return feed.recovery(waiting, next == null ? null : LocalTime.ofInstant(next, clock.getZone()));
    }

    /**
     * Whether the run will try the provider again by itself, so a person's Pause holds it rather than being ignored.
     *
     * @return {@code true} while a wake is scheduled, {@code false} otherwise
     */
    boolean isRecoveryWaiting() {
        final RecoveryState state = recovery;
        return state != null && state.isWaiting();
    }

    /** Forgets the recovery: the run resumed, paused anew or ended. */
    void clearRecovery() {
        if (recovery != null) {
            log.debug("recovery cleared");
            recovery = null;
            mirror.review().publishRecovery(null);
        }
    }

    // The countdown moves once a second; publishing only a changed second keeps the FX queue quiet.
    private void publishCountdown() {
        final RecoveryState state = recovery;
        if (state == null || !state.isWaiting()) {
            return;
        }
        final RecoveryState now = state.at(clock.instant());
        if (now.secondsLeft() != state.secondsLeft()) {
            recovery = now;
            mirror.review().publishRecovery(now);
        }
    }

    /**
     * Publishes why a run paused: on an error, its notice and then the error, whatever its code, so a request the provider
     * refused as invalid reaches the provider-error state too; on a review pause, the
     * segment it stopped at.
     *
     * @param paused the pause the engine reported
     */
    void publishPause(final Paused paused) {
        final AppError error = paused.error();
        final String segmentId = paused.segmentId();
        log.debug("paused for {} at segment {}", paused.reason(), segmentId);
        clearWait("the run paused");
        clearRecovery();
        if (paused.reason() == PauseReason.ON_ERROR && error != null) {
            log.debug("pause on error {}: publishing the provider error", error.code());
            mirror.review().publishPauseNotice(noticeOf(paused));
            mirror.review().publishProviderError(error);
        } else if (segmentId != null
                && (paused.reason() == PauseReason.ON_FLAGGED || paused.reason() == PauseReason.AFTER_SEGMENT)) {
            mirror.review().publishReviewPauseSegment(segmentId);
        }
    }

    /**
     * What the provider-error banner names about a pause on an error.
     *
     * @param paused the pause, whose error is non-null
     * @return the notice: the segment, the last failed call's kind and the pauses spent
     */
    private PauseNotice noticeOf(final Paused paused) {
        final AppError error = paused.error();
        final String segmentId = paused.segmentId();
        final PauseNotice notice = new PauseNotice(
                error == null ? null : error.code(),
                segmentId == null ? "" : feed.locatorOf(segmentId),
                lastFailed,
                paused.pauses(),
                paused.pausesBeforeFlagging());
        lastFailed = null;
        log.debug("pause notice for segment {}: last failed call {}", segmentId, notice.kind());
        return notice;
    }
}
