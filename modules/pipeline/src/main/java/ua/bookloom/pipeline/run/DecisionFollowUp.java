package ua.bookloom.pipeline.run;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.glossary.ScanEntries;
import ua.bookloom.pipeline.index.BookIndex;
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
    private final @Nullable String analysisLanguage;
    private final RollingSummaryKeeper keeper;
    private final SummaryRepository summaries;
    private final SegmentRepository segments;
    private final GlossaryRepository glossary;
    private final BookIndex index;
    private final RunSinks sinks;
    private final RoutedCalls calls;
    private final ChunkSteps announced;
    private final List<Segment> decidedInUnit = new ArrayList<>();
    private @Nullable String summary;

    /**
     * The book a run follows up.
     *
     * @param projectId the project's id
     * @param analysisLanguage the language the book's names are analysed in; {@code null} when it has none
     * @param index the book's index, whose ordinals the decided story is read by
     */
    record BookFacts(String projectId, @Nullable String analysisLanguage, BookIndex index) {}

    DecisionFollowUp(
            final BookFacts book,
            final RollingSummaryKeeper keeper,
            final RunStores stores,
            final RunSinks sinks,
            final RoutedCalls calls,
            final ChunkSteps announced) {
        this.projectId = Objects.requireNonNull(book.projectId(), "projectId");
        this.analysisLanguage = book.analysisLanguage();
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.summaries = Objects.requireNonNull(stores, "stores").summaries();
        this.segments = stores.segments();
        this.glossary = stores.glossary();
        this.index = Objects.requireNonNull(book.index(), "index");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.announced = Objects.requireNonNull(announced, "announced");
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
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(latest.error(), ERROR)));
        }
        final Optional<RollingSummary> stored = Objects.requireNonNull(latest.data(), "latest");
        stored.ifPresent(this::hold);
        final Result<List<SegmentRecord>> records = segments.all(projectId);
        if (records.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(records.error(), ERROR)));
        }
        keeper.seed(Objects.requireNonNull(records.data(), "records"), stored);
        log.debug("Read the latest summary projectId={} present={}", projectId, summary != null);
        return Optional.empty();
    }

    /**
     * The summary text a draft is shown: the model's, when it wrote one.
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
     * @return empty to go on, or how the run ended
     */
    Optional<RunEnd> decided(
            final WorkList work,
            final WorkItem item,
            final SegmentRecord record,
            final List<GlossaryEntry> chunkGlossary) {
        final Segment segment = item.segment();
        sinks.pending().deferred(DeferralRegister.unknownGender(projectId, segment, chunkGlossary));
        decidedInUnit.add(segment);
        final Result<Optional<RollingSummary>> counted = keeper.onDecided(segment, record);
        if (counted.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(counted.error(), ERROR)));
        }
        announce(Objects.requireNonNull(counted.data(), "counted"), "count");
        return work.endsUnit(item) ? unitEnded(work, item) : Optional.empty();
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
        announced.summarizing(unitId);
        return calls.untilAnswered(work, null, () -> keeper.onUnitEnd(unitId))
                .fold(
                        refreshed -> {
                            announced.summarized(unitId, refreshed);
                            announce(refreshed, "unit end");
                            return Optional.<RunEnd>empty();
                        },
                        Optional::of);
    }

    /**
     * Proposes the names every segment decided so far holds into the unit's last commit, commits it at once, and
     * announces the entries the commit actually added — the commit skips a term the person holds or removed.
     */
    private Optional<AppError> scanNames(final WorkList work, final String unitId) {
        final List<Integer> scanned = decidedStory(work);
        final Result<ScanEntries.Found> found = FrequencyScan.found(
                        projectId, index, scanned, analysisLanguage, glossary)
                .flatMap(scan -> extendHeldForms(scan.heldForms()).map(extended -> scan));
        if (found.isErr()) {
            return Optional.of(Objects.requireNonNull(found.error(), ERROR));
        }
        final List<GlossaryEntry> proposals =
                Objects.requireNonNull(found.data(), "found").proposals();
        if (proposals.isEmpty()) {
            logScan(unitId, scanned.size(), 0, 0);
            return Optional.empty();
        }
        sinks.pending().proposed(proposals);
        final Result<Integer> added = sinks.pending().flush().flatMap(applied -> countHeld(proposals));
        if (added.isErr()) {
            return Optional.of(Objects.requireNonNull(added.error(), ERROR));
        }
        final int count = Objects.requireNonNull(added.data(), "added");
        logScan(unitId, scanned.size(), proposals.size(), count);
        if (count > 0) {
            sinks.emit().accept(MemoryEvents.namesAdded(count));
        }
        return Optional.empty();
    }

    // A held entry the scan found in a new form lists that form too, so the windows that name only it carry the entry.
    private Result<Integer> extendHeldForms(final Map<String, List<String>> heldForms) {
        for (final Map.Entry<String, List<String>> forms : heldForms.entrySet()) {
            final Result<Optional<GlossaryEntry>> stored = glossary.update(projectId, forms.getKey(), entry -> {
                final Set<String> all = new LinkedHashSet<>(entry.forms());
                all.addAll(forms.getValue());
                return entry.withForms(List.copyOf(all));
            });
            if (stored.isErr()) {
                return Result.err(Objects.requireNonNull(stored.error(), ERROR));
            }
        }
        log.debug("Held entry forms extended project={} entries={}", projectId, heldForms.size());
        return Result.ok(heldForms.size());
    }

    // The decided story segments in the order they were decided, as ordinals of the book's index.
    private List<Integer> decidedStory(final WorkList work) {
        final List<Segment> story = Stream.concat(work.decidedSegments().stream(), decidedInUnit.stream())
                .filter(work.story()::holds)
                .toList();
        final List<Integer> ordinals = story.stream()
                .map(segment -> index.ordinalOf(segment.id()))
                .flatMapToInt(OptionalInt::stream)
                .boxed()
                .toList();
        log.debug("Decided story segments={} missingFromIndex={}", story.size(), story.size() - ordinals.size());
        return ordinals;
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
        if (!hold(version)) {
            log.debug(
                    "Summary refresh not announced trigger={} version={}: the text shown to drafts is unchanged",
                    trigger,
                    version.version());
            return;
        }
        log.debug("Summary refresh announced trigger={} version={}", trigger, version.version());
        if (log.isTraceEnabled()) {
            log.trace("Summary text now shown to drafts {}", summary);
        }
        sinks.emit().accept(MemoryEvents.summaryRefreshed(version));
    }

    /**
     * Holds the model-written summary of {@code version}. The deterministic text in its source side — the glossary
     * lines and the heading titles — is not a summary of the story and is never shown as one: the chunk's names travel
     * in their own glossary block, and a list of chapter titles told the model nothing (fixture run, 2026-10-02).
     *
     * @return {@code true} if the text shown to later drafts changed, {@code false} otherwise
     */
    private boolean hold(final RollingSummary version) {
        final @Nullable String previous = summary;
        summary = version.target().isBlank() ? null : version.target();
        log.debug(
                "Summary held version={} modelWritten={} changed={}",
                version.version(),
                summary != null,
                !Objects.equals(previous, summary));
        return !Objects.equals(previous, summary);
    }
}
