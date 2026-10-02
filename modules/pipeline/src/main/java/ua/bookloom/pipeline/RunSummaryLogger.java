package ua.bookloom.pipeline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.ContextAssembled;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.pipeline.StageStarted;

/**
 * Writes one INFO {@code run summary} line a minute while a job runs, and a last one when it ends: segments accepted,
 * flagged, kept verbatim and pending, model calls with their average and 95th-percentile time, tokens a second,
 * timeouts so far and the segment being translated.
 *
 * <p>Driven by the job's own events rather than a timer: the first event at least {@link #INTERVAL} after the last
 * line writes the next one, so no thread is added, a paused job (which sends no events) writes nothing, and a test
 * moves the clock instead of sleeping. A stalled call still produces events — each attempt and its failure — so a
 * stall is summarised too. Events arrive on the job thread only, which is the one thread that touches this object.
 */
@Slf4j
final class RunSummaryLogger {

    /** How long at least between two periodic lines. */
    static final Duration INTERVAL = Duration.ofSeconds(60);

    private static final double PERCENTILE = 0.95;
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final String NONE = "-";

    private final Clock clock;
    private final List<Long> callMillis = new ArrayList<>();
    // Set by the first event, so a job built long before it runs does not write a line at once.
    private @Nullable Instant lastLine;
    private @Nullable JobProgress progress;
    private String locator = NONE;
    private int timeouts;
    private long completionTokens;
    private long generationMillis;

    RunSummaryLogger(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Takes in one job event, and writes a line when a minute has passed or the job has ended. */
    void onEvent(final JobEvent event) {
        Objects.requireNonNull(event, "event");
        switch (event) {
            case SegmentStarted started -> locator = started.locator();
            case SegmentDecided decided -> progress = decided.progress();
            case Paused paused -> progress = paused.progress();
            case Resumed resumed -> progress = resumed.progress();
            case StageStarted stage -> progress = stage.progress();
            case ModelCallFinished finished -> record(finished);
            case Finished _ -> {
                write("final");
                return;
            }
            case ModelCallStarted _,
                    SegmentDrafted _,
                    MemoryUpdated _,
                    ContextAssembled _,
                    RoundStarted _,
                    RecoveryWaiting _ -> {
                // Nothing to count: these say what is happening, the counts change on the events above.
            }
        }
        final Instant now = clock.instant();
        final Instant last = lastLine;
        if (last == null) {
            lastLine = now;
        } else if (Duration.between(last, now).compareTo(INTERVAL) >= 0) {
            write("periodic");
        }
    }

    private void record(final ModelCallFinished finished) {
        callMillis.add(finished.elapsed().toMillis());
        if (finished.failure() == ErrorCode.timeout) {
            timeouts++;
        }
        final TokenUsage usage = finished.usage();
        if (usage == null || usage.completion() == null) {
            return;
        }
        completionTokens += Objects.requireNonNull(usage.completion(), "completion");
        final Duration generation = usage.generation();
        generationMillis += (generation == null ? finished.elapsed() : generation).toMillis();
    }

    private void write(final String kind) {
        lastLine = clock.instant();
        final JobProgress at = progress;
        log.info(
                "run summary {} accepted={} flagged={} verbatim={} pending={} calls={} avgCallMs={} p95CallMs={}"
                        + " tokensPerSecond={} timeouts={} current={}",
                kind,
                at == null ? 0 : at.accepted(),
                at == null ? 0 : at.flagged(),
                at == null ? 0 : at.keptVerbatim(),
                at == null ? 0 : at.pending(),
                callMillis.size(),
                averageMillis(),
                percentileMillis(),
                tokensPerSecond(),
                timeouts,
                locator);
    }

    private long averageMillis() {
        return Math.round(
                callMillis.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private long percentileMillis() {
        if (callMillis.isEmpty()) {
            return 0;
        }
        final List<Long> sorted = callMillis.stream().sorted().toList();
        final int index = (int) Math.ceil(PERCENTILE * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private String tokensPerSecond() {
        if (generationMillis <= 0) {
            return NONE;
        }
        return String.format(Locale.ROOT, "%.1f", completionTokens * MILLIS_PER_SECOND / generationMillis);
    }
}
