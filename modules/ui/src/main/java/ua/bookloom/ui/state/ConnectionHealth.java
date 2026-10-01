package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;

/**
 * Keeps how the model server has been answering this run: when it last answered, which attempts failed lately and how
 * fast it judges. The drafting speed is the run's own pace figure, passed in, so it is never counted twice.
 *
 * <p>A failed attempt counts only while its call has not been answered: a call is known by its kind and the segments it
 * is about, and when a later attempt of the same call — the client's own retry, or the person's Retry now — is answered,
 * its earlier failures are forgotten, so the title bar never warns of a call that in the end succeeded.
 *
 * <p>Not thread-safe: {@link RunSession} calls it under its publish lock.
 */
@Slf4j
final class ConnectionHealth {

    /** A failure older than this no longer says anything about the server now. */
    static final Duration RECENT = Duration.ofMinutes(10);

    /** Which call an attempt belongs to: the same kind about the same segments is the same call tried again. */
    private record CallKey(CallKind kind, @Nullable String segmentId, List<String> segmentIds) {

        static CallKey of(final ModelCallFinished event) {
            return new CallKey(event.kind(), event.segmentId(), event.segmentIds());
        }
    }

    private record Failure(Instant at, boolean timedOut, CallKey call) {}

    private final ThroughputMeter judging = new ThroughputMeter(CallKind.JUDGE);
    private final Deque<Failure> failures = new ArrayDeque<>();
    private @Nullable Instant lastAnswerAt;

    /**
     * Counts one finished attempt.
     *
     * @param event the non-null attempt's end
     * @param at when it ended
     */
    void finished(final ModelCallFinished event, final Instant at) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(at, "at");
        final ErrorCode failure = event.failure();
        if (failure == null) {
            lastAnswerAt = at;
            judging.finished(event);
            forgetFailuresOf(CallKey.of(event));
            return;
        }
        if (failure == ErrorCode.cancelled) {
            log.debug("connection: a {} attempt was interrupted by the person; not counted as a failure", event.kind());
            return;
        }
        log.debug("connection: a {} attempt failed with {}", event.kind(), failure);
        failures.addLast(new Failure(at, failure == ErrorCode.timeout, CallKey.of(event)));
    }

    private void forgetFailuresOf(final CallKey call) {
        final int before = failures.size();
        failures.removeIf(failed -> failed.call().equals(call));
        if (failures.size() < before) {
            log.debug(
                    "connection: a {} call about {} was answered; its {} failed attempt(s) no longer count",
                    call.kind(),
                    call.segmentIds(),
                    before - failures.size());
        }
    }

    /**
     * The status as of {@code now}, forgetting failures older than {@link #RECENT}.
     *
     * @param now the current instant
     * @param draftTokensPerSecond the run's drafting speed, or {@code null} before one is known
     * @return the status; never null
     */
    ConnectionStatus snapshot(final Instant now, final @Nullable Double draftTokensPerSecond) {
        final Instant cutoff = now.minus(RECENT);
        while (!failures.isEmpty() && failures.peekFirst().at().isBefore(cutoff)) {
            failures.removeFirst();
        }
        final Instant answered = lastAnswerAt;
        final Duration since = answered == null
                ? null
                : Duration.ofSeconds(Duration.between(answered, now).toSeconds());
        final int timeouts = (int) failures.stream().filter(Failure::timedOut).count();
        return new ConnectionStatus(since, timeouts, failures.size(), draftTokensPerSecond, judging.tokensPerSecond());
    }
}
