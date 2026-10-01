package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
 * <p>Not thread-safe: {@link RunSession} calls it under its publish lock.
 */
@Slf4j
final class ConnectionHealth {

    /** A failure older than this no longer says anything about the server now. */
    static final Duration RECENT = Duration.ofMinutes(10);

    private record Failure(Instant at, boolean timedOut) {}

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
            return;
        }
        if (failure == ErrorCode.cancelled) {
            log.debug("connection: a {} attempt was interrupted by the person; not counted as a failure", event.kind());
            return;
        }
        log.debug("connection: a {} attempt failed with {}", event.kind(), failure);
        failures.addLast(new Failure(at, failure == ErrorCode.timeout));
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
