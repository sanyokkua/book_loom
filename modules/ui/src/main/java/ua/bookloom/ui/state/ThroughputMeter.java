package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;

/**
 * Tokens per second over the last draft calls, from the usage each call reported.
 *
 * <p>Only drafts count: a judge's short answer would make a slow model look fast. When a provider reported no usage
 * the pipeline has already estimated the completion tokens and stamped the call, so this class never estimates. Not
 * thread-safe: {@link RunSession} calls it under its publish lock.
 */
@Slf4j
final class ThroughputMeter {

    /** How many finished draft calls the rate is taken over. */
    static final int WINDOW = 20;

    private record Sample(int completionTokens, Duration generation, boolean estimated) {}

    private final Deque<Sample> samples = new ArrayDeque<>();

    void finished(final ModelCallFinished event) {
        if (event.kind() != CallKind.DRAFT) {
            return;
        }
        final TokenUsage usage = event.usage();
        final Integer tokens = usage == null ? null : usage.completion();
        if (usage == null || tokens == null) {
            log.debug("throughput: a draft call reported no completion tokens, not counted");
            return;
        }
        final Duration reported = usage.generation();
        final Duration generation = reported == null ? event.elapsed() : reported;
        samples.addLast(new Sample(tokens, generation, event.usageEstimated()));
        if (samples.size() > WINDOW) {
            samples.removeFirst();
        }
        log.debug("throughput: draft call counted, {} tokens, window {}", tokens, samples.size());
    }

    @Nullable
    Double tokensPerSecond() {
        long tokens = 0;
        long nanos = 0;
        for (final Sample sample : samples) {
            tokens += sample.completionTokens();
            nanos += sample.generation().toNanos();
        }
        return nanos <= 0 ? null : tokens * (double) Duration.ofSeconds(1).toNanos() / nanos;
    }

    boolean isEstimated() {
        return samples.stream().anyMatch(Sample::estimated);
    }

    /** Builds the figure for a snapshot; {@code timeLeft} and {@code elapsed} come from the run clock. */
    Throughput snapshot(final @Nullable Duration timeLeft, final Duration elapsed) {
        return new Throughput(tokensPerSecond(), isEstimated(), timeLeft, Objects.requireNonNull(elapsed, "elapsed"));
    }
}
