package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.SegmentOutcome;

/**
 * Decides a chunk's drafted segments one at a time and stores each decision: the record, the memory entry, the
 * learned terms, the events, and what follows a decision. Used from the job thread only.
 */
@Slf4j
final class ChunkDeciding {

    private final RoutedCalls calls;
    private final RunSinks sinks;
    private final MemoryReuse memory;
    private final TermLearning learning;
    private final DecisionFollowUp followUp;
    private final SegmentEvents events;
    private final DraftShortcuts shortcuts;

    ChunkDeciding(final RunSinks sinks, final TermLearning learning, final ChunkDrafting.Collaborators collaborators) {
        this.sinks = sinks;
        this.learning = learning;
        this.calls = collaborators.calls();
        this.memory = collaborators.memory();
        this.followUp = collaborators.followUp();
        this.events = collaborators.events();
        this.shortcuts = collaborators.shortcuts();
    }

    Optional<RunEnd> decideEach(final Current current, final List<WorkItem> items, final ChunkDecider decider) {
        for (final WorkItem item : items) {
            final Optional<RunEnd> end =
                    SegmentLogContext.within(item.segment().id(), () -> decideOne(current, item, decider));
            if (end.isPresent()) {
                return end;
            }
        }
        return Optional.empty();
    }

    private Optional<RunEnd> decideOne(final Current current, final WorkItem item, final ChunkDecider decider) {
        final String segmentId = item.segment().id();
        return switch (calls.untilAnsweredOrFlagged(
                current.work(),
                segmentId,
                new RoutedCalls.StepName("decide", segmentId),
                decider::nextDecision,
                decider::flagCurrent)) {
            case Step.Stopped<SegmentOutcome>(final RunEnd stopped) -> Optional.of(stopped);
            case Step.Done<SegmentOutcome>(final SegmentOutcome outcome) -> record(current, item, outcome);
        };
    }

    private Optional<RunEnd> record(final Current current, final WorkItem item, final SegmentOutcome outcome) {
        final SegmentRecord record = OutcomeRecords.decided(
                item.record(), outcome, current.drafts().snapshot(item.segment().id()));
        sinks.pending()
                .decided(
                        record,
                        memory.entryFor(record, item.segment(), current.work().unitSegments(item)));
        current.drafts().decided(record.segmentId());
        learning.decided(item.segment(), record);
        shortcuts.decided(item.segment(), record);
        sinks.recorder().decided(record.status());
        final JobProgress progress = current.work().apply(item, record.status(), record.path());
        final ErrorCode reason = record.status() == SegmentStatus.FLAGGED ? OutcomeRecords.reportCode(record) : null;
        log.debug(
                "Decided segmentId={} status={} reason={} path={}",
                record.segmentId(),
                record.status(),
                reason,
                record.path());
        events.decided(record, reason, progress);
        if (record.path() == SegmentPath.TM_REUSE) {
            sinks.emit().accept(memory.announced(record.segmentId()));
        }
        return followUp.decided(current.work(), item, record, current.context().glossary())
                .or(() -> sinks.boundaries()
                        .afterDecision(RunBoundaries.Decision.of(current.work(), item, record), progress));
    }
}
