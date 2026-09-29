package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The segments a run still has to decide, in document order, and the counts that move as it decides them.
 *
 * <p>The list is the project's PENDING records, so a run that starts after an earlier one stopped begins at the
 * first segment nobody decided; a FLAGGED record is never one. The counts are read once and moved per decision, so
 * no query runs while the job works.
 */
@Slf4j
public final class WorkList {

    /**
     * The auxiliary kinds the run leaves out until the Book Brief's switches reach it: with every switch off, the
     * whole auxiliary unit is kept as source. It is never removed from the book, because the export compares full
     * segment counts.
     */
    static final Set<SegmentKind> KEPT_AS_SOURCE = new AlsoTranslate(false, false, false, false).keptKinds();

    private final List<Unit> body;
    private final int segments;
    private final List<WorkItem> pending;
    private int next;
    private int accepted;
    private int flagged;
    private int pendingCount;
    private int lastSection;

    private WorkList(
            final List<Unit> body, final int segments, final List<WorkItem> pending, final SegmentCounts counts) {
        this.body = List.copyOf(body);
        this.segments = segments;
        this.pending = List.copyOf(pending);
        accepted = counts.accepted() + counts.revised();
        flagged = counts.flagged();
        pendingCount = counts.pending();
        log.debug(
                "Built work list sections={} segments={} pending={} accepted={} flagged={}",
                body.size(),
                segments,
                this.pending.size(),
                accepted,
                flagged);
    }

    /**
     * Reads a project's stored records and lays them over its opened book.
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
        final Result<List<SegmentRecord>> records = stores.segments().all(projectId);
        if (records.isErr()) {
            return Result.err(Objects.requireNonNull(records.error(), "error"));
        }
        final Result<SegmentCounts> counts = stores.segments().countsByStatus(projectId, KEPT_AS_SOURCE);
        if (counts.isErr()) {
            return Result.err(Objects.requireNonNull(counts.error(), "error"));
        }
        final Map<String, SegmentRecord> byId = new HashMap<>();
        Objects.requireNonNull(records.data(), "records").forEach(record -> byId.put(record.segmentId(), record));
        return Result.ok(of(document, byId, Objects.requireNonNull(counts.data(), "counts")));
    }

    private static WorkList of(
            final Document document, final Map<String, SegmentRecord> byId, final SegmentCounts counts) {
        final List<Unit> body =
                document.units().stream().filter(unit -> !unit.isAuxiliary()).toList();
        final List<WorkItem> pending = new ArrayList<>();
        int total = 0;
        for (int section = 0; section < body.size(); section++) {
            total += body.get(section).segments().size();
            addPending(pending, body.get(section), section, byId);
        }
        return new WorkList(body, total, pending, counts);
    }

    private static void addPending(
            final List<WorkItem> pending, final Unit unit, final int section, final Map<String, SegmentRecord> byId) {
        log.debug(
                "Inspecting unit id={} section={} segments={}",
                unit.id(),
                section,
                unit.segments().size());
        for (final Segment segment : unit.segments()) {
            final SegmentRecord record = byId.get(segment.id());
            if (record != null && record.status() == SegmentStatus.PENDING) {
                pending.add(new WorkItem(segment, section, record));
                log.debug("Queued pending segment id={} section={}", segment.id(), section);
            }
        }
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
        return body.get(Objects.requireNonNull(item, "item").section()).segments();
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
        final boolean endsSection = !hasPending() || pending.get(next).section() != item.section();
        log.debug(
                "Checked section boundary segmentId={} section={} endsSection={}",
                item.segment().id(),
                item.section(),
                endsSection);
        return endsSection;
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
        return body.size();
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
        final JobProgress progress = new JobProgress(stage, section, body.size(), accepted, flagged, pendingCount);
        log.debug(
                "Built progress stage={} section={}/{} accepted={} flagged={} pending={}",
                stage,
                section,
                body.size(),
                accepted,
                flagged,
                pendingCount);
        return progress;
    }
}
