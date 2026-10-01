package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.judge.JudgeDeferral;
import ua.bookloom.pipeline.memory.RollingSummaryKeeper;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * What follows a decision besides the decision itself: the deferrals it leaves, the rolling summary's count, and at a
 * unit's end the summary's refresh and — for a body unit — the names the book has introduced so far, each glossary and
 * summary update announced once. It also holds the latest summary, which every later draft is shown; the person
 * cannot edit a summary, so the versions this run writes are the only change it needs to follow.
 *
 * <p>Used from the job thread only, which is why the state is plain fields.
 */
@Slf4j
final class DecisionFollowUp {

    private final String projectId;
    private final @Nullable String sourceLanguage;
    private final RollingSummaryKeeper keeper;
    private final SummaryRepository summaries;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final RunSinks sinks;
    private final RoutedCalls calls;
    private final List<Segment> decidedInUnit = new ArrayList<>();
    private @Nullable String summary;

    DecisionFollowUp(
            final String projectId,
            @Nullable final String sourceLanguage,
            final RollingSummaryKeeper keeper,
            final RunStores stores,
            final RunSinks sinks,
            final RoutedCalls calls) {
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.sourceLanguage = sourceLanguage;
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.summaries = Objects.requireNonNull(stores, "stores").summaries();
        this.segments = stores.segments();
        this.glossary = stores.glossary();
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /**
     * Reads the latest stored summary, so a run started after an earlier one shows its first drafts what that run
     * summarized, and starts the summary keeper's count from the segments that run already accepted.
     *
     * @return empty, or the failed end when the summary or the stored records could not be read
     */
    Optional<RunEnd> readSummary() {
        final Result<Optional<RollingSummary>> latest = summaries.latest(projectId);
        if (latest.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(latest.error(), "error")));
        }
        final Optional<RollingSummary> stored = Objects.requireNonNull(latest.data(), "latest");
        stored.ifPresent(this::hold);
        final Result<List<SegmentRecord>> records = segments.all(projectId);
        if (records.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(records.error(), "error")));
        }
        keeper.seed(Objects.requireNonNull(records.data(), "records"), stored);
        log.debug("Read the latest summary projectId={} present={}", projectId, summary != null);
        return Optional.empty();
    }

    /**
     * The summary text a draft is shown: the model's when it wrote one, else the deterministic one.
     *
     * @return the text, or {@code null} while there is no summary or it is empty
     */
    @Nullable
    String summary() {
        return summary;
    }

    /**
     * Follows one recorded decision.
     *
     * @param work the run's work list, which already moved past the item
     * @param item the decided item
     * @param record its decided record
     * @param chunkGlossary the glossary the chunk was drafted with
     * @param judged every judge deferral of the chunk so far
     * @return empty to go on, or how the run ended
     */
    Optional<RunEnd> decided(
            final WorkList work,
            final WorkItem item,
            final SegmentRecord record,
            final List<GlossaryEntry> chunkGlossary,
            final List<JudgeDeferral> judged) {
        final Segment segment = item.segment();
        sinks.pending().deferred(deferralsOf(segment, chunkGlossary, judged));
        decidedInUnit.add(segment);
        final Result<Optional<RollingSummary>> counted = keeper.onDecided(segment, record);
        if (counted.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(counted.error(), "error")));
        }
        announce(Objects.requireNonNull(counted.data(), "counted"), "count");
        return work.endsUnit(item) ? unitEnded(work, item) : Optional.empty();
    }

    private List<Deferral> deferralsOf(
            final Segment segment, final List<GlossaryEntry> chunkGlossary, final List<JudgeDeferral> judged) {
        final List<JudgeDeferral> own = judged.stream()
                .filter(deferral -> deferral.segmentId().equals(segment.id()))
                .toList();
        return Stream.concat(
                        DeferralRegister.fromJudge(projectId, own).stream(),
                        DeferralRegister.unknownGender(projectId, segment, chunkGlossary).stream())
                .toList();
    }

    // The names go first, so the refreshed summary can already list a name the unit introduced.
    private Optional<RunEnd> unitEnded(final WorkList work, final WorkItem item) {
        final String unitId = item.segment().unit();
        log.debug("Unit ended unitId={} body={} decidedInUnit={}", unitId, work.isBody(item), decidedInUnit.size());
        if (work.isBody(item)) {
            final Optional<AppError> failed = scanNames(work, unitId);
            if (failed.isPresent()) {
                return Optional.of(RoutedCalls.failedBy(failed.get()));
            }
        }
        decidedInUnit.clear();
        return switch (calls.untilAnswered(work, null, () -> keeper.onUnitEnd(unitId))) {
            case Step.Stopped<Optional<RollingSummary>>(final RunEnd end) -> Optional.of(end);
            case Step.Done<Optional<RollingSummary>>(final Optional<RollingSummary> refreshed) -> {
                announce(refreshed, "unit end");
                yield Optional.empty();
            }
        };
    }

    /**
     * Proposes the names every segment decided so far holds into the unit's last commit, commits it at once, and
     * announces the entries the commit actually added — the commit skips a term the person holds or removed.
     */
    private Optional<AppError> scanNames(final WorkList work, final String unitId) {
        final List<Segment> scanned = Stream.concat(work.decidedSegments().stream(), decidedInUnit.stream())
                .toList();
        final Result<List<GlossaryEntry>> proposed =
                FrequencyScan.newTerms(projectId, scanned, sourceLanguage, glossary);
        if (proposed.isErr()) {
            return Optional.of(Objects.requireNonNull(proposed.error(), "error"));
        }
        final List<GlossaryEntry> proposals = Objects.requireNonNull(proposed.data(), "proposals");
        if (proposals.isEmpty()) {
            logScan(unitId, scanned.size(), 0, 0);
            return Optional.empty();
        }
        sinks.pending().proposed(proposals);
        final Result<Integer> added = sinks.pending().flush().flatMap(applied -> countHeld(proposals));
        if (added.isErr()) {
            return Optional.of(Objects.requireNonNull(added.error(), "error"));
        }
        final int count = Objects.requireNonNull(added.data(), "added");
        logScan(unitId, scanned.size(), proposals.size(), count);
        if (count > 0) {
            sinks.emit().accept(MemoryEvents.namesAdded(count));
        }
        return Optional.empty();
    }

    private Result<Integer> countHeld(final List<GlossaryEntry> proposals) {
        return glossary.all(projectId)
                .map(held -> (int) proposals.stream().filter(held::contains).count());
    }

    private static void logScan(final String unitId, final int scanned, final int proposals, final int added) {
        log.debug(
                "Unit-end name scan unitId={} segmentsScanned={} proposals={} added={} skippedAtCommit={}",
                unitId,
                scanned,
                proposals,
                added,
                proposals - added);
    }

    private void announce(final Optional<RollingSummary> refreshed, final String trigger) {
        if (refreshed.isEmpty()) {
            return;
        }
        final RollingSummary version = refreshed.get();
        hold(version);
        log.debug("Summary refresh announced trigger={} version={}", trigger, version.version());
        if (log.isTraceEnabled()) {
            log.trace("Summary text now shown to drafts {}", summary);
        }
        sinks.emit().accept(MemoryEvents.summaryRefreshed(version));
    }

    private void hold(final RollingSummary version) {
        final String text = version.target().isBlank() ? version.source() : version.target();
        summary = text.isBlank() ? null : text;
    }
}
