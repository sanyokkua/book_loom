package ua.bookloom.pipeline.run;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
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
import ua.bookloom.pipeline.Decision;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * The segments a run still has to decide, in document order, and the counts that move as it decides them.
 *
 * <p>The list is the project's PENDING records, so a run that starts after an earlier one stopped begins at the
 * first segment nobody decided; a FLAGGED record is never one. The counts are read once and moved per decision, so
 * no query runs while the job works.
 */
@Slf4j
public final class WorkList {

    private static final int PRECEDING_TARGETS = 3;

    /**
     * The auxiliary kinds the run leaves out until the Book Brief's switches reach it: with every switch off, the
     * whole auxiliary unit is kept as source. It is never removed from the book, because the export compares full
     * segment counts.
     */
    static final Set<SegmentKind> KEPT_AS_SOURCE = new AlsoTranslate(false, false, false, false).keptKinds();

    private final int sections;
    private final int segments;
    private final List<WorkItem> pending;
    private final ArrayDeque<String> precedingTargets = new ArrayDeque<>();
    private int next;
    private int accepted;
    private int flagged;
    private int pendingCount;
    private int lastSection;
    private int contextSection = -1;

    private WorkList(final int sections, final int segments, final List<WorkItem> pending, final SegmentCounts counts) {
        this.sections = sections;
        this.segments = segments;
        this.pending = List.copyOf(pending);
        accepted = counts.accepted() + counts.revised();
        flagged = counts.flagged();
        pendingCount = counts.pending();
        log.debug(
                "Built work list sections={} segments={} pending={} accepted={} flagged={}",
                sections,
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
        return new WorkList(body.size(), total, pending, counts);
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
     * Returns the segment the run decides next, without moving past it.
     *
     * @return the first undecided item; only valid while {@link #hasPending()}
     */
    public WorkItem next() {
        final WorkItem item = pending.get(next);
        log.debug(
                "Selected pending segment id={} index={} section={}",
                item.segment().id(),
                next,
                item.section());
        return item;
    }

    /**
     * Returns the translation progress as it stands.
     *
     * @return the counts with the section of the next undecided segment, or of the last decided one at the end
     */
    public JobProgress currentTranslationProgress() {
        return progress(hasPending() ? pending.get(next).section() : lastSection);
    }

    /**
     * Moves past a decided item and updates the counts and the preceding-target context.
     *
     * @param item the non-null item just decided, which must be {@link #next()}
     * @param decision the non-null decision made for it
     * @return the progress after the decision
     */
    public JobProgress apply(final WorkItem item, final Decision decision) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(decision, "decision");
        log.debug(
                "Applying decision segmentId={} status={} section={}",
                item.segment().id(),
                decision.segment().status(),
                item.section());
        lastSection = item.section();
        next++;
        pendingCount--;
        recordCount(decision);
        recordPrecedingTarget(item, decision);
        return progress(lastSection);
    }

    /**
     * Returns the targets accepted just before an item in its own section, at most three and never across a section
     * boundary.
     *
     * @param item the non-null item about to be drafted
     * @return the context, empty when the item opens its section or the run
     */
    public DraftContext draftContextFor(final WorkItem item) {
        Objects.requireNonNull(item, "item");
        if (contextSection != item.section()) {
            return DraftContext.empty();
        }
        return new DraftContext(List.copyOf(precedingTargets));
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
        return sections;
    }

    /**
     * Returns the number of segments in the body units.
     *
     * @return the segment count, the auxiliary unit left out
     */
    public int segmentCount() {
        return segments;
    }

    private JobProgress progress(final int section) {
        final JobProgress progress =
                new JobProgress(JobStage.TRANSLATE, section, sections, accepted, flagged, pendingCount);
        log.debug(
                "Built progress section={}/{} accepted={} flagged={} pending={}",
                section,
                sections,
                accepted,
                flagged,
                pendingCount);
        return progress;
    }

    private void recordCount(final Decision decision) {
        if (decision.segment().status() == SegmentStatus.ACCEPTED) {
            accepted++;
            return;
        }
        flagged++;
        final @Nullable AppError reason = decision.flagReason();
        log.debug(
                "Recorded flagged decision segmentId={} reason={}",
                decision.segment().id(),
                reason == null ? null : reason.code());
    }

    private void recordPrecedingTarget(final WorkItem item, final Decision decision) {
        if (contextSection != item.section()) {
            precedingTargets.clear();
            contextSection = item.section();
        }
        if (decision.segment().status() != SegmentStatus.ACCEPTED) {
            return;
        }
        precedingTargets.addLast(Objects.requireNonNull(decision.segment().targetInner(), "accepted target"));
        if (precedingTargets.size() > PRECEDING_TARGETS) {
            precedingTargets.removeFirst();
        }
    }
}
