package ua.bookloom.pipeline;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;

/** Keeps all run-thread decisions and their observable counts together. */
@Slf4j
final class JobProgressTracker {

    private final Document source;
    private final List<SegmentWork> pending;
    private final List<FlaggedSegment> flaggedSegments = new ArrayList<>();
    private final Map<String, Segment> decisions = new HashMap<>();
    private @Nullable Document materialized;
    private int next;
    private int accepted;
    private int flagged;
    private int lastSection;

    JobProgressTracker(final Document source) {
        this.source = Objects.requireNonNull(source, "source");
        pending = pendingSegments(source);
        log.debug(
                "Initialized job progress format={} units={} pending={}",
                source.format(),
                source.units().size(),
                pending.size());
    }

    boolean hasPending() {
        final boolean hasPending = next < pending.size();
        log.debug("Checked pending decisions next={} total={} hasPending={}", next, pending.size(), hasPending);
        return hasPending;
    }

    SegmentWork next() {
        final SegmentWork work = pending.get(next);
        log.debug(
                "Selected pending segment id={} index={} section={}",
                work.segment().id(),
                next,
                work.section());
        return work;
    }

    JobProgress currentTranslationProgress() {
        return logProgress("translation", progress(JobStage.TRANSLATE, currentSection()));
    }

    JobProgress exportProgress() {
        return logProgress("export", progress(JobStage.EXPORT, lastSection));
    }

    JobProgress apply(final SegmentWork work, final Decision decision) {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(decision, "decision");
        log.debug(
                "Applying decision segmentId={} status={} section={}",
                work.segment().id(),
                decision.segment().status(),
                work.section());
        decisions.put(decision.segment().id(), decision.segment());
        materialized = null;
        lastSection = work.section();
        next++;
        recordDecision(decision);
        return logProgress("decision", progress(JobStage.TRANSLATE, lastSection));
    }

    boolean endsSection(final SegmentWork work) {
        final boolean endsSection = !hasPending() || next().section() != work.section();
        log.debug(
                "Checked section boundary segmentId={} section={} endsSection={}",
                work.segment().id(),
                work.section(),
                endsSection);
        return endsSection;
    }

    boolean isComplete() {
        final boolean complete = !hasPending();
        log.debug(
                "Checked translation completion complete={} accepted={} flagged={} pending={}",
                complete,
                accepted,
                flagged,
                pending.size() - next);
        return complete;
    }

    Document decidedDocument() {
        if (materialized != null) {
            log.debug("Reusing materialized decision document decisions={}", decisions.size());
            return materialized;
        }
        log.debug(
                "Materializing decision document decisions={} units={}",
                decisions.size(),
                source.units().size());
        materialized = materialize();
        return materialized;
    }

    BookFormat format() {
        return source.format();
    }

    @Nullable
    String declaredLanguage() {
        return source.declaredLang();
    }

    int sectionCount() {
        return source.units().size();
    }

    int segmentCount() {
        return source.units().stream().mapToInt(unit -> unit.segments().size()).sum();
    }

    JobReport report(final JobState end, @Nullable final Path written, @Nullable final AppError error) {
        log.debug(
                "Building job report state={} total={} accepted={} flagged={} written={} errorCode={}",
                end,
                pending.size(),
                accepted,
                flagged,
                written,
                error == null ? null : error.code());
        return new JobReport(source.format(), end, pending.size(), accepted, flagged, flaggedSegments, written, error);
    }

    private int currentSection() {
        final int section = hasPending() ? next().section() : lastSection;
        log.debug("Selected current progress section={} hasPending={}", section, next < pending.size());
        return section;
    }

    private JobProgress progress(final JobStage stage, final int section) {
        final JobProgress progress =
                new JobProgress(stage, section, source.units().size(), accepted, flagged, pending.size() - next);
        log.debug(
                "Built progress stage={} section={} accepted={} flagged={} pending={}",
                stage,
                section,
                accepted,
                flagged,
                progress.pending());
        return progress;
    }

    private void recordDecision(final Decision decision) {
        if (decision.segment().status() == SegmentStatus.ACCEPTED) {
            accepted++;
            log.debug(
                    "Recorded accepted decision segmentId={} accepted={} flagged={}",
                    decision.segment().id(),
                    accepted,
                    flagged);
            return;
        }
        flagged++;
        final AppError reason = Objects.requireNonNull(decision.flagReason(), "flag reason");
        flaggedSegments.add(new FlaggedSegment(decision.segment().id(), reason.code()));
        log.debug(
                "Recorded flagged decision segmentId={} reason={} accepted={} flagged={}",
                decision.segment().id(),
                reason.code(),
                accepted,
                flagged);
    }

    private static JobProgress logProgress(final String source, final JobProgress progress) {
        log.debug(
                "Recorded {} progress stage={} section={}/{} accepted={} flagged={} pending={}",
                source,
                progress.stage(),
                progress.section(),
                progress.sections(),
                progress.accepted(),
                progress.flagged(),
                progress.pending());
        return progress;
    }

    private static List<SegmentWork> pendingSegments(final Document document) {
        log.debug(
                "Collecting pending segments units={} format={}",
                document.units().size(),
                document.format());
        final List<SegmentWork> work = new ArrayList<>();
        for (int section = 0; section < document.units().size(); section++) {
            addPending(work, document.units().get(section), section);
        }
        final List<SegmentWork> pending = List.copyOf(work);
        log.debug("Collected pending segments count={}", pending.size());
        return pending;
    }

    private static void addPending(final List<SegmentWork> work, final Unit unit, final int section) {
        log.debug(
                "Inspecting unit id={} section={} segments={}",
                unit.id(),
                section,
                unit.segments().size());
        for (final Segment segment : unit.segments()) {
            if (segment.status() == SegmentStatus.PENDING) {
                work.add(new SegmentWork(segment, section));
                log.debug("Queued pending segment id={} section={}", segment.id(), section);
            }
        }
    }

    private Document materialize() {
        final List<Unit> units =
                source.units().stream().map(this::materializeUnit).toList();
        return source.withUnits(units);
    }

    private Unit materializeUnit(final Unit unit) {
        final List<Segment> segments = unit.segments().stream()
                .map(segment -> decisions.getOrDefault(segment.id(), segment))
                .toList();
        return unit.withSegments(segments);
    }
}
