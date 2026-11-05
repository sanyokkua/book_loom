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
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.glossary.FrequencyScan;
import ua.bookloom.pipeline.glossary.ScanEntries;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * What follows a decision besides the decision itself: the deferrals it leaves, and at a body unit's end the names the
 * book has introduced so far, a glossary update announced once.
 *
 * <p>Used from the job thread only, which is why the state is plain fields.
 */
@Slf4j
final class DecisionFollowUp {

    private final String projectId;
    private final @Nullable String analysisLanguage;
    private final GlossaryRepository glossary;
    private final BookIndex index;
    private final RunSinks sinks;
    private final List<Segment> decidedInUnit = new ArrayList<>();

    /**
     * The book a run follows up.
     *
     * @param projectId the project's id
     * @param analysisLanguage the language the book's names are analysed in; {@code null} when it has none
     * @param index the book's index, whose ordinals the decided story is read by
     */
    record BookFacts(String projectId, @Nullable String analysisLanguage, BookIndex index) {}

    DecisionFollowUp(final BookFacts book, final RunStores stores, final RunSinks sinks) {
        this.projectId = Objects.requireNonNull(book.projectId(), "projectId");
        this.analysisLanguage = book.analysisLanguage();
        this.glossary = Objects.requireNonNull(stores, "stores").glossary();
        this.index = Objects.requireNonNull(book.index(), "index");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
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
        log.debug(
                "Decision followed segment={} status={} endsUnit={}",
                segment.id(),
                record.status(),
                work.endsUnit(item));
        return work.endsUnit(item) ? unitEnded(work, item) : Optional.empty();
    }

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
        return Optional.empty();
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
}
