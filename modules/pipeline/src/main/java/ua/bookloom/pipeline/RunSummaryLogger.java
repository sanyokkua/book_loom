package ua.bookloom.pipeline;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.ContextAssembled;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ProofreadForecast;
import ua.bookloom.api.pipeline.ProofreadRoundStarted;
import ua.bookloom.api.pipeline.ProofreadWindowRead;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepsPlanned;

/**
 * Writes one INFO {@code run summary} line a minute while a job runs, and a last one when it ends: segments accepted,
 * flagged, kept verbatim and pending, model calls with their average and 95th-percentile time (over the newest
 * {@link #PERCENTILE_WINDOW} calls, so the memory held does not grow with the run), tokens a second,
 * timeouts so far and the segment being translated. The final line adds the run's calls by kind, read from the call
 * ledger, so every stage is counted from what each call reported.
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

    /** How many of the newest call times the percentile is taken over, so a night's run holds a fixed number. */
    static final int PERCENTILE_WINDOW = 1_000;

    private static final double PERCENTILE = 0.95;
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final String NONE = "-";

    private final Clock clock;
    private final CallLedger ledger;
    // The ledger's records after this mark are the run's: the job is built just before it runs.
    private final long mark;
    private final Deque<Long> recentMillis = new ArrayDeque<>();
    private long calls;
    private long totalMillis;
    // Set by the first event, so a job built long before it runs does not write a line at once.
    private @Nullable Instant lastLine;
    private @Nullable JobProgress progress;
    private String locator = NONE;
    private int timeouts;
    // By segment: a segment decided again (a repair, a retry) counts its latest decision only.
    private long completionTokens;
    private long generationMillis;

    RunSummaryLogger(final Clock clock) {
        this(clock, CallLedger.none());
    }

    /** A summary whose final line counts, by kind, the calls the ledger keeps from now on. */
    RunSummaryLogger(final Clock clock, final CallLedger ledger) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.mark = ledger.mark();
    }

    /**
     * Takes in one job event, and writes a line when a minute has passed or the job has ended. Only the decisions, the
     * stages, the pauses and the finished calls change the counts; the other events say what is happening.
     */
    void onEvent(final JobEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof Finished) {
            write("final");
            return;
        }
        count(event);
        writeWhenDue();
    }

    private void count(final JobEvent event) {
        switch (event) {
            case SegmentStarted started -> locator = started.locator();
            case SegmentDecided decided -> decide(decided);
            case Paused paused -> progress = paused.progress();
            case Resumed resumed -> progress = resumed.progress();
            case StageStarted stage -> progress = stage.progress();
            case ModelCallFinished finished -> record(finished);
            case Finished _,
                    ModelCallStarted _,
                    SegmentDrafted _,
                    MemoryUpdated _,
                    ContextAssembled _,
                    RecoveryWaiting _,
                    BatchStarted _,
                    CallSnapshotUpdated _,
                    StepsPlanned _,
                    StepChanged _,
                    BriefSampleRead _,
                    ChapterSummaryKept _,
                    ProofreadForecast _,
                    ProofreadRoundStarted _,
                    ProofreadWindowRead _ -> {}
        }
    }

    private void writeWhenDue() {
        final Instant now = clock.instant();
        final Instant last = lastLine;
        if (last == null) {
            lastLine = now;
        } else if (Duration.between(last, now).compareTo(INTERVAL) >= 0) {
            write("periodic");
        }
    }

    private void decide(final SegmentDecided decided) {
        progress = decided.progress();
    }

    private void record(final ModelCallFinished finished) {
        final long millis = finished.elapsed().toMillis();
        calls++;
        totalMillis += millis;
        recentMillis.addLast(millis);
        if (recentMillis.size() > PERCENTILE_WINDOW) {
            recentMillis.removeFirst();
        }
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
                        + " tokensPerSecond={} timeouts={} current={}{}",
                kind,
                at == null ? 0 : at.accepted(),
                at == null ? 0 : at.flagged(),
                at == null ? 0 : at.keptVerbatim(),
                at == null ? 0 : at.pending(),
                calls,
                averageMillis(),
                percentileMillis(),
                tokensPerSecond(),
                timeouts,
                locator,
                "final".equals(kind) ? finalTail() : "");
    }

    // Counted from what each call reported, so every stage of the run is in it and a missing figure stays missing.
    private String finalTail() {
        final List<CallRecord> records = ledger.since(mark);
        final CallKindTotals totals = CallKindTotals.of(records);
        final String line = PrepSummary.byKind(totals.totals());
        return " cost=" + totalCost(totals)
                + (line.isEmpty() ? "" : " byKind[" + line + "]")
                + PrepSummary.secondsPerCall(records);
    }

    // A dash when no call reported a price; a free model's reported 0 is a 0.
    private static String totalCost(final CallKindTotals totals) {
        final List<BigDecimal> priced = totals.totals().values().stream()
                .flatMap(kindTotals -> kindTotals.costTotal().stream())
                .toList();
        return priced.isEmpty()
                ? NONE
                : priced.stream().reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString();
    }

    /** How many call times are held for the percentile; never more than {@link #PERCENTILE_WINDOW}. */
    int heldCallTimes() {
        return recentMillis.size();
    }

    private long averageMillis() {
        return calls == 0 ? 0 : Math.round((double) totalMillis / calls);
    }

    private long percentileMillis() {
        if (recentMillis.isEmpty()) {
            return 0;
        }
        final List<Long> sorted = recentMillis.stream().sorted().toList();
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
