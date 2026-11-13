package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadForecast;
import ua.bookloom.api.pipeline.ProofreadRoundStarted;
import ua.bookloom.api.pipeline.ProofreadWindowRead;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;

/**
 * Follows the stage a run is in and what its Proofread stage does: it publishes the stage when it changes (the stage
 * chip and the strip read it, and it is withdrawn when the run ends), feeds the ledger the Proofread forecast, the
 * marks and the rounds, and turns each read window into a live row and a log entry. Not thread-safe: {@link
 * RunSession} calls it under its publish lock.
 */
@Slf4j
final class StageTracker {

    private final LiveSection live;
    private final RunLedger progress;
    private final LiveCallState liveCalls;
    private final ActivityLogFeed feed;
    private ProofreadCoverage coverage = ProofreadCoverage.NONE;
    private @Nullable RunStage shown;

    StageTracker(
            final LiveSection live,
            final RunLedger progress,
            final LiveCallState liveCalls,
            final ActivityLogFeed feed) {
        this.live = Objects.requireNonNull(live, "live");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.liveCalls = Objects.requireNonNull(liveCalls, "liveCalls");
        this.feed = Objects.requireNonNull(feed, "feed");
    }

    void forecast(final ProofreadForecast forecast) {
        coverage = forecast.coverage();
        progress.proofread().forecast(forecast);
        final RunStage now = shown;
        if (now != null) {
            show(new RunStage(now.stage(), coverage, now.round()));
        }
    }

    void started(final JobStage stage) {
        show(new RunStage(stage, coverage, 0));
    }

    // Only Max reads in two rounds; its second round is a stage of its own in the log, as on the stage chip.
    Optional<LogEntry> round(final ProofreadRoundStarted round) {
        log.debug(
                "proofread round {} of {} started: {} of {} windows to read",
                round.round(),
                round.rounds(),
                round.toRead(),
                round.windows());
        progress.proofread().roundStarted(round.round());
        show(new RunStage(JobStage.PROOFREAD, coverage, round.round()));
        return round.rounds() > 1
                ? Optional.of(feed.stageStarted(JobStage.PROOFREAD, round.round()))
                : Optional.empty();
    }

    LogEntry windowRead(final ProofreadWindowRead read) {
        liveCalls.windowRead(read);
        return feed.windowRead(read);
    }

    // A flagged segment, or an accepted one with a finding left, is a mark the Proofread stage reads (design D15).
    void decided(final SegmentDecided decided) {
        final SegmentDetail detail = decided.detail();
        if (decided.status() == SegmentStatus.FLAGGED
                || (detail != null && !detail.findingKinds().isEmpty())) {
            progress.proofread().marked();
        }
    }

    void ended() {
        log.debug("run stage withdrawn: the run ended");
        shown = null;
        live.publishStage(null);
    }

    private void show(final RunStage stage) {
        if (stage.equals(shown)) {
            return;
        }
        log.debug("run stage now {} round {} (coverage {})", stage.stage(), stage.round(), stage.coverage());
        shown = stage;
        live.publishStage(stage);
    }
}
