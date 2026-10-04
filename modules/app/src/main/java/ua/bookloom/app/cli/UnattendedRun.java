package ua.bookloom.app.cli;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.ContextAssembled;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ProviderProbe;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.pipeline.CallKindTotals;

/**
 * A run with nobody at the terminal, wired like a window run: it pauses on a provider error and waits through an
 * outage by itself (the same recovery schedule and probe), and since nobody can answer a pause only a person ends — a
 * wrong key, a missing model, an outage past its limit — it stops the run there instead of waiting forever, so what
 * was translated can still be written. It also tallies what the run report says: model calls, outages, flagged
 * segments and the run's last progress.
 */
@Slf4j
final class UnattendedRun {

    /** One outage the run waited through: when it began, how many wakes it took and how it ended. */
    record Outage(Instant downSince, ErrorCode code, int wakes, String ended) {}

    /** A flagged segment and why: the run's reason and the kinds of finding recorded against it. */
    record FlaggedDetail(String segmentId, @Nullable ErrorCode reason, List<String> findingKinds) {}

    private final TranslationJob job;
    private final PrintStream out;
    private final int stopAfter;
    private final Map<Instant, Outage> outages = new LinkedHashMap<>();
    private final List<FlaggedDetail> flagged = new ArrayList<>();
    private final AtomicReference<@Nullable String> stopReason = new AtomicReference<>();
    private @Nullable JobProgress lastProgress;
    private final CallKindTotals kindTotals = new CallKindTotals();
    private int modelCalls;
    private int decidedSegments;
    private Duration modelTime = Duration.ZERO;
    private int waits;

    private UnattendedRun(TranslationJob job, PrintStream out, int stopAfter) {
        this.job = job;
        this.out = out;
        this.stopAfter = stopAfter;
    }

    /**
     * Wires a job to run unattended.
     *
     * @param job the job, not yet run; never null
     * @param probe the probe each wake checks the provider with; never null
     * @param maxOutage the longest outage waited through before the run is stopped; never null
     * @param stopAfter how many decided segments end the run early, or zero to run the whole book
     * @param out where the outage lines go; never null
     * @return the watcher, whose tallies are complete once the job's run returns
     */
    static UnattendedRun attach(
            TranslationJob job, ProviderProbe probe, Duration maxOutage, int stopAfter, PrintStream out) {
        final UnattendedRun run =
                new UnattendedRun(Objects.requireNonNull(job, "job"), Objects.requireNonNull(out), stopAfter);
        job.pauseAt(Set.of(PausePoint.ON_ERROR));
        job.recoverWith(Objects.requireNonNull(probe, "probe"), Objects.requireNonNull(maxOutage, "maxOutage"));
        job.subscribe(run::onEvent);
        log.info("translate command run is unattended: pause on error, recovery up to {}", maxOutage);
        return run;
    }

    private void onEvent(JobEvent event) {
        switch (event) {
            case Paused paused -> onPaused(paused);
            case RecoveryWaiting waiting -> onWaiting(waiting);
            case Resumed resumed -> onResumed();
            case SegmentDecided decided -> onDecided(decided);
            case ModelCallFinished finished -> {
                kindTotals.record(finished);
                modelCalls++;
                modelTime = modelTime.plus(finished.elapsed());
            }
            case StageStarted _,
                    ModelCallStarted _,
                    Finished _,
                    SegmentStarted _,
                    SegmentDrafted _,
                    MemoryUpdated _,
                    ContextAssembled _,
                    RoundStarted _,
                    BatchStarted _ -> {}
        }
    }

    private void onPaused(Paused paused) {
        final AppError error = paused.error();
        if (error != null && !paused.recoversByItself()) {
            stop(error.code() + " (" + error.title() + ") needs a person; nobody is at the terminal");
        }
    }

    private void onWaiting(RecoveryWaiting waiting) {
        final boolean first = !outages.containsKey(waiting.downSince());
        outages.put(
                waiting.downSince(),
                new Outage(
                        waiting.downSince(),
                        waiting.error().code(),
                        waiting.attempt(),
                        waiting.status().name()));
        if (first) {
            out.println("Provider " + waiting.error().code() + " — waiting and retrying by itself, down since "
                    + waiting.downSince());
        }
        switch (waiting.status()) {
            case WAITING -> waits++;
            case GAVE_UP -> stop("the provider is still " + waiting.error().code() + " after the outage limit");
            case HELD -> stop("the run was held while waiting for the provider");
        }
    }

    private void onResumed() {
        outages.replaceAll((since, outage) -> "WAITING".equals(outage.ended())
                ? new Outage(since, outage.code(), outage.wakes(), "RESUMED")
                : outage);
    }

    private void onDecided(SegmentDecided decided) {
        lastProgress = decided.progress();
        decidedSegments++;
        if (stopAfter > 0 && decidedSegments >= stopAfter) {
            stop("the " + stopAfter + " segments asked for with --stop-after were decided");
        }
        if (decided.status() == SegmentStatus.FLAGGED) {
            final SegmentDetail detail = decided.detail();
            flagged.add(new FlaggedDetail(
                    decided.segmentId(), decided.reason(), detail == null ? List.of() : detail.findingKinds()));
        }
    }

    private void stop(String reason) {
        if (stopReason.compareAndSet(null, reason)) {
            log.warn("translate command stops the run: {}", reason);
            out.println("Stopping the run: " + reason);
            job.cancel();
        }
    }

    /** Why the watcher stopped the run, or {@code null} when it did not. */
    @Nullable
    String stopReason() {
        return stopReason.get();
    }

    /** The run's last progress, or {@code null} when no segment was decided. */
    @Nullable
    JobProgress lastProgress() {
        return lastProgress;
    }

    List<Outage> outages() {
        return List.copyOf(outages.values());
    }

    List<FlaggedDetail> flagged() {
        return List.copyOf(flagged);
    }

    CallKindTotals kindTotals() {
        return kindTotals;
    }

    int modelCalls() {
        return modelCalls;
    }

    Duration modelTime() {
        return modelTime;
    }

    int waits() {
        return waits;
    }
}
