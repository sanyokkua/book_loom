package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The segments a run still has to decide, in document order, and the counts that move as it decides them.
 *
 * <p>The list is the project's PENDING records of the body units, then of the auxiliary unit, so a run that starts
 * after an earlier one stopped begins at the first segment nobody decided; a FLAGGED record is never one. A record of
 * an auxiliary kind the brief keeps as source is no work and no pending count, and its stored status is never
 * touched. The brief is read again at every {@link #refresh()}, so a switch changed during a pause takes effect at
 * the next section; the counts move per decision in between, so no query runs while a section is worked.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class WorkList {

    private final RunStores stores;
    private final String projectId;
    private final List<Unit> units;
    private final int bodyUnits;
    private final int segments;
    private List<WorkItem> pending = List.of();
    private List<Segment> decided = List.of();
    private int next;
    private int accepted;
    private int flagged;
    private int pendingCount;
    private int lastSection;

    private WorkList(final RunStores stores, final String projectId, final Document document) {
        this.stores = stores;
        this.projectId = projectId;
        final List<Unit> body =
                document.units().stream().filter(unit -> !unit.isAuxiliary()).toList();
        final List<Unit> ordered = new ArrayList<>(body);
        document.units().stream().filter(Unit::isAuxiliary).forEach(ordered::add);
        this.units = List.copyOf(ordered);
        this.bodyUnits = body.size();
        this.segments = body.stream().mapToInt(unit -> unit.segments().size()).sum();
    }

    /**
     * Reads a project's stored records and the brief's switches and lays them over its opened book.
     *
     * @param stores the non-null stores to read from
     * @param projectId the non-null project id
     * @param document the non-null opened book of that project
     * @return the work list, or the storage error that stopped the read
     */
    public static Result<WorkList> read(final RunStores stores, final String projectId, final Document document) {
        Objects.requireNonNull(stores, "stores");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(document, "document");
        final WorkList work = new WorkList(stores, projectId, document);
        return work.load("start").map(count -> work);
    }

    /**
     * Reads the auxiliary kinds the project's brief keeps as source, as the brief stands now.
     *
     * @param stores the non-null stores to read from
     * @param projectId the non-null project id
     * @return the kinds, or {@code validation} when the project is gone, or the storage error that stopped the read
     */
    static Result<Set<SegmentKind>> keptKinds(final RunStores stores, final String projectId) {
        return stores.projects()
                .find(projectId)
                .flatMap(found -> found.isEmpty()
                        ? Result.<Set<SegmentKind>>err(AppError.of(
                                ErrorCode.validation, "This project is not known", "Import the book again."))
                        : Result.ok(found.get().brief().alsoTranslate().keptKinds()));
    }

    /**
     * Reads the stored records, the brief's switches and the counts again and starts the list over from the first
     * segment nobody has decided; everything decided so far must already be committed.
     *
     * @return the number of segments still to decide, or the storage error that stopped the read
     */
    public Result<Integer> refresh() {
        return load("section end");
    }

    private Result<Integer> load(final String occasion) {
        return keptKinds(stores, projectId)
                .flatMap(kept -> stores.segments()
                        .all(projectId)
                        .flatMap(records -> stores.segments()
                                .countsByStatus(projectId, kept)
                                .map(counts -> install(occasion, kept, records, counts))));
    }

    private int install(
            final String occasion,
            final Set<SegmentKind> kept,
            final List<SegmentRecord> records,
            final SegmentCounts counts) {
        final Map<String, SegmentRecord> byId = new HashMap<>();
        records.forEach(record -> byId.put(record.segmentId(), record));
        final List<WorkItem> queued = new ArrayList<>();
        for (int section = 0; section < units.size(); section++) {
            addPending(queued, units.get(section), section, byId, kept);
        }
        pending = List.copyOf(queued);
        decided = decidedOf(byId, kept);
        next = 0;
        accepted = counts.accepted() + counts.revised();
        flagged = counts.flagged();
        pendingCount = counts.pending();
        log.debug(
                "Read work list occasion={} keptKinds={} keptRecords={} sections={} segments={} pending={} decided={}"
                        + " accepted={} flagged={}",
                occasion,
                kept,
                counts.sourceKept(),
                bodyUnits,
                segments,
                pending.size(),
                decided.size(),
                accepted,
                flagged);
        return pending.size();
    }

    private List<Segment> decidedOf(final Map<String, SegmentRecord> byId, final Set<SegmentKind> kept) {
        return units.stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> {
                    final SegmentRecord record = byId.get(segment.id());
                    return record != null && record.status() != SegmentStatus.PENDING && !record.isKeptAsSource(kept);
                })
                .toList();
    }

    private static void addPending(
            final List<WorkItem> queued,
            final Unit unit,
            final int section,
            final Map<String, SegmentRecord> byId,
            final Set<SegmentKind> kept) {
        log.debug(
                "Inspecting unit id={} section={} segments={}",
                unit.id(),
                section,
                unit.segments().size());
        for (final Segment segment : unit.segments()) {
            final SegmentRecord record = byId.get(segment.id());
            if (record != null && record.isKeptAsSource(kept)) {
                log.debug("Kept as source segmentId={} kind={}", segment.id(), record.kind());
            } else if (record != null && record.status() == SegmentStatus.PENDING) {
                queued.add(new WorkItem(segment, section, record));
                log.debug("Queued pending segment id={} section={}", segment.id(), section);
            }
        }
    }

    /**
     * Returns the pending segments of the first section that has any, in document order.
     *
     * @return the leading run of {@link #remaining()} that shares one section; never null, empty when none is left
     */
    public List<WorkItem> nextSection() {
        if (!hasPending()) {
            return List.of();
        }
        int to = next + 1;
        while (to < pending.size()
                && pending.get(to).section() == pending.get(next).section()) {
            to++;
        }
        return List.copyOf(pending.subList(next, to));
    }

    /**
     * Reports whether a segment is still to be decided.
     *
     * @return {@code true} if one is, {@code false} otherwise
     */
    public boolean hasPending() {
        final boolean hasPending = next < pending.size();
        log.debug("Checked pending decisions next={} total={} hasPending={}", next, pending.size(), hasPending);
        return hasPending;
    }

    /**
     * Returns the segments still to decide, in document order.
     *
     * @return never null; empty when every segment is decided
     */
    public List<WorkItem> remaining() {
        return pending.subList(next, pending.size());
    }

    /**
     * Returns every segment of an item's unit, decided ones included, in document order — the segments a draft's
     * preceding targets are taken from.
     *
     * @param item the non-null item whose unit is asked for
     * @return never null; holds the item's own segment
     */
    public List<Segment> unitSegments(final WorkItem item) {
        return units.get(Objects.requireNonNull(item, "item").section()).segments();
    }

    /**
     * Returns the translation progress as it stands.
     *
     * @return the counts with the section of the next undecided segment, or of the last decided one at the end
     */
    public JobProgress currentTranslationProgress() {
        return progress(JobStage.TRANSLATE, hasPending() ? pending.get(next).section() : lastSection);
    }

    /**
     * Returns the counts as preparation starts, before any segment of this run is decided.
     *
     * @return the counts under the {@code PREP} stage, at the section of the first undecided segment
     */
    public JobProgress preparationProgress() {
        return progress(JobStage.PREP, hasPending() ? pending.get(next).section() : lastSection);
    }

    /**
     * Moves past a decided item and updates the counts.
     *
     * @param item the non-null item just decided, which must be the first of {@link #remaining()}
     * @param status the non-null status it was decided with, {@code ACCEPTED} or {@code FLAGGED}
     * @return the progress after the decision
     */
    public JobProgress apply(final WorkItem item, final SegmentStatus status) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(status, "status");
        log.debug(
                "Applying decision segmentId={} status={} section={}",
                item.segment().id(),
                status,
                item.section());
        lastSection = item.section();
        next++;
        pendingCount--;
        if (status == SegmentStatus.ACCEPTED) {
            accepted++;
        } else {
            flagged++;
        }
        return progress(JobStage.TRANSLATE, lastSection);
    }

    /**
     * Reports whether an item, once decided, is the last of its section.
     *
     * @param item the non-null item just decided
     * @return {@code true} if nothing follows it in its section, {@code false} otherwise
     */
    public boolean endsSection(final WorkItem item) {
        final boolean endsSection = isBody(item) && endsUnit(item);
        log.debug(
                "Checked section boundary segmentId={} section={} endsSection={}",
                item.segment().id(),
                item.section(),
                endsSection);
        return endsSection;
    }

    /**
     * Reports whether an item, once decided, is the last of its unit to decide — the auxiliary unit included, which
     * {@link #endsSection} leaves out.
     *
     * @param item the non-null item just decided
     * @return {@code true} if nothing follows it in its unit, {@code false} otherwise
     */
    public boolean endsUnit(final WorkItem item) {
        Objects.requireNonNull(item, "item");
        final boolean endsUnit = !hasPending() || pending.get(next).section() != item.section();
        log.debug(
                "Checked unit end segmentId={} section={} endsUnit={}",
                item.segment().id(),
                item.section(),
                endsUnit);
        return endsUnit;
    }

    /**
     * Reports whether an item's unit is one of the book's body units.
     *
     * @param item the non-null item asked about
     * @return {@code true} for a body unit, {@code false} for the auxiliary unit
     */
    public boolean isBody(final WorkItem item) {
        return Objects.requireNonNull(item, "item").section() < bodyUnits;
    }

    /**
     * Returns the segments decided when the list was last read, whatever their status, in document order; a segment
     * kept as source by choice is not one.
     *
     * @return never null; empty when nothing was decided
     */
    public List<Segment> decidedSegments() {
        return decided;
    }

    /**
     * Reports whether every item has been decided.
     *
     * @return {@code true} if none is left, {@code false} otherwise
     */
    public boolean isComplete() {
        final boolean complete = !hasPending();
        log.debug("Checked translation completion complete={} pending={}", complete, pendingCount);
        return complete;
    }

    /**
     * Returns the number of body units, the auxiliary unit never counted.
     *
     * @return the section count
     */
    public int sectionCount() {
        return bodyUnits;
    }

    /**
     * Returns the number of segments in the body units.
     *
     * @return the segment count, the auxiliary unit left out
     */
    public int segmentCount() {
        return segments;
    }

    private JobProgress progress(final JobStage stage, final int section) {
        final JobProgress progress = new JobProgress(stage, section, bodyUnits, accepted, flagged, pendingCount);
        log.debug(
                "Built progress stage={} section={}/{} accepted={} flagged={} pending={}",
                stage,
                section,
                bodyUnits,
                accepted,
                flagged,
                pendingCount);
        return progress;
    }
}
