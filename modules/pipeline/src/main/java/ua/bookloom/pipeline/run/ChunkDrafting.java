package ua.bookloom.pipeline.run;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.memory.ProtectedMask;

/**
 * Drafts one segment of a chunk: a shortcut or a context-matched memory target stands in for the call when it can, a
 * batch the segment was drafted in is taken, and otherwise the segment is translated alone. Used from the job thread
 * only.
 */
@Slf4j
final class ChunkDrafting {

    private final RunSteps steps;
    private final RunSettings settings;
    private final PrecedingTargets preceding;
    private final MemoryReuse memory;
    private final RoutedCalls calls;
    private final DecisionFollowUp followUp;
    private final SegmentEvents events;
    private final DraftShortcuts shortcuts;
    private final BatchStage batches;
    private final ChunkSteps announced;

    ChunkDrafting(final RunSteps steps, final RunSettings settings, final Collaborators collaborators) {
        this.steps = steps;
        this.settings = settings;
        this.preceding = collaborators.preceding();
        this.memory = collaborators.memory();
        this.calls = collaborators.calls();
        this.followUp = collaborators.followUp();
        this.events = collaborators.events();
        this.shortcuts = collaborators.shortcuts();
        this.batches = collaborators.batches();
        this.announced = collaborators.announced();
    }

    /** The collaborators a chunk's drafting and deciding share. */
    record Collaborators(
            PrecedingTargets preceding,
            MemoryReuse memory,
            RoutedCalls calls,
            DecisionFollowUp followUp,
            SegmentEvents events,
            DraftShortcuts shortcuts,
            BatchStage batches,
            ChunkSteps announced) {}

    /**
     * Drafts one segment behind its protected spans and with its context package, keeping the draft in the chunk —
     * unless a shortcut decides it without a call (nothing to translate, or an identical auxiliary text), or a
     * context-matched memory target passes its checks, which stands in for the draft.
     */
    Step<DraftOutcome> draft(final Current current, final WorkItem item) {
        final Segment segment = item.segment();
        begun(current, item);
        final ProtectedMask mask = current.context().mask(segment);
        final DraftOutcome shortcut =
                shortcuts.take(segment, mask, current.context().gate(), current.loop(), current.drafts());
        if (shortcut != null) {
            events.drafted(shortcut, current.loop());
            announced.drafted(segment.id());
            return new Step.Done<>(shortcut);
        }
        final List<Segment> unitSegments = current.work().unitSegments(item);
        final Result<List<String>> earlierMaskedTargets = preceding.earlierMaskedTargets(
                unitSegments, segment, settings.dial().precedingTargets(), current.drafts());
        if (earlierMaskedTargets.isErr()) {
            return new Step.Stopped<>(
                    RoutedCalls.failedBy(Objects.requireNonNull(earlierMaskedTargets.error(), ERROR)));
        }
        final MemoryReuse.Offer offer =
                memory.offer(segment, unitSegments, mask, current.context().gate(), current.loop());
        final ContextPackage context =
                contextOf(current, segment, Objects.requireNonNull(earlierMaskedTargets.data(), "targets"), offer);
        final DraftOutcome.Reused reused = offer.reused();
        if (reused != null) {
            current.drafts().drafted(reused, context.snapshot());
            announced.drafted(segment.id());
            return new Step.Done<>(reused);
        }
        return drafted(current, new Ready(item, mask, offer), context);
    }

    private void begun(final Current current, final WorkItem item) {
        events.started(item.segment(), current.work().position(item));
        announced.drafting(item.segment().id());
    }

    /** A segment that needs a call, with the mask it is shown and what the memory offered it. */
    private record Ready(WorkItem item, ProtectedMask mask, MemoryReuse.Offer offer) {}

    private Step<DraftOutcome> drafted(final Current current, final Ready ready, final ContextPackage context) {
        final Segment segment = ready.item().segment();
        final ProtectedMask mask = ready.mask();
        final Optional<RunEnd> batchEnd = batches.ensure(current, ready.item(), mask, ready.offer());
        if (batchEnd.isPresent()) {
            return new Step.Stopped<>(batchEnd.get());
        }
        final List<PromptSection> sections = current.batches().takeSent(segment.id());
        final ContextSnapshot snapshot = context.snapshot().withSent(sections);
        events.contextAssembled(segment.id(), snapshot);
        final DraftOutcome batched = current.batches().take(segment.id());
        final Step<DraftOutcome> drafted = batched != null
                ? new Step.Done<>(batched)
                : calls.untilAnsweredOrFlagged(
                        current.work(),
                        segment.id(),
                        new RoutedCalls.StepName("draft", segment.id()),
                        () -> current.translator()
                                .translateSplit(
                                        segment, context.draftContext(), mask, steps.splitter(), current.budget()),
                        error -> new DraftOutcome.FlaggedAtOnce(segment, segment.masked(), List.of(), error));
        if (drafted instanceof Step.Done<DraftOutcome>(final DraftOutcome outcome)) {
            current.drafts().drafted(outcome, snapshot);
            events.drafted(outcome, current.loop());
            announced.drafted(segment.id(), outcome);
        }
        return drafted;
    }

    private ContextPackage contextOf(
            final Current current, final Segment segment, final List<String> targets, final MemoryReuse.Offer offer) {
        final ContextPackage context =
                current.requests().contextFor(segment, targets, offer.lookup(), followUp.summary());
        if (log.isTraceEnabled()) {
            log.trace(
                    "Preceding targets segmentId={} targets={}",
                    segment.id(),
                    context.draftContext().precedingTargets());
        }
        return context;
    }
}
