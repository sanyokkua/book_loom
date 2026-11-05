package ua.bookloom.pipeline.run;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TranslationMemory;
import ua.bookloom.pipeline.project.SegmentLocators;

/**
 * Drafts one segment of a chunk: a shortcut or a context-matched memory target stands in for the call when it can, a
 * batch the segment was drafted in is taken, and otherwise the segment is translated alone. Used from the job thread
 * only.
 */
@Slf4j
final class ChunkDrafting {

    private final RunSteps steps;
    private final PrecedingTargets preceding;
    private final MemoryReuse memory;
    private final RoutedCalls calls;
    private final SegmentEvents events;
    private final DraftShortcuts shortcuts;
    private final BatchStage batches;
    private final ChunkSteps announced;

    ChunkDrafting(final RunSteps steps, final Collaborators collaborators) {
        this.steps = steps;
        this.preceding = collaborators.preceding();
        this.memory = collaborators.memory();
        this.calls = collaborators.calls();
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
            ChunkSteps announced) {

        private static BatchStage batchStageOf(
                final RunSteps steps,
                final RunSettings settings,
                final MemoryReuse memory,
                final PrecedingTargets preceding,
                final RoutedCalls calls) {
            return new BatchStage(
                    steps.batch(),
                    settings,
                    memory,
                    preceding,
                    calls,
                    steps.planner().frame().examples(),
                    steps.calls());
        }

        /** Builds the collaborators of one run, each from the stores and sinks it is given. */
        static Collaborators wire(
                final RunSteps steps,
                final RunSettings settings,
                final RunStores stores,
                final RunSinks sinks,
                final BookIndex index,
                final Document document) {
            final Map<String, SegmentLocator> locators = SegmentLocators.of(document);
            final PrecedingTargets preceding =
                    new PrecedingTargets(stores.segments(), settings.projectId(), sinks.pending());
            final MemoryReuse memory =
                    new MemoryReuse(new TranslationMemory(stores.tm(), settings.projectId()), locators);
            final RoutedCalls calls = new RoutedCalls(sinks.boundaries());
            final ChunkSteps announced = new ChunkSteps(steps.calls().steps(), settings.dial());
            final DecisionFollowUp followUp = new DecisionFollowUp(
                    new DecisionFollowUp.BookFacts(
                            settings.projectId(), settings.frame().analysisLanguage(), index),
                    steps.summary(),
                    stores,
                    sinks,
                    calls,
                    announced);
            return new Collaborators(
                    preceding,
                    memory,
                    calls,
                    followUp,
                    new SegmentEvents(sinks.emit(), locators, steps.calls()),
                    DraftShortcuts.forFrame(settings.frame()),
                    batchStageOf(steps, settings, memory, preceding, calls),
                    announced);
        }
    }

    /**
     * Drafts one segment behind its protected spans, keeping the draft and the snapshot of its call in the chunk —
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
        final MemoryReuse.Offer offer =
                memory.offer(segment, unitSegments, mask, current.context().gate(), current.loop());
        final DraftOutcome.Reused reused = offer.reused();
        if (reused != null) {
            current.drafts().drafted(reused, ContextSnapshot.noCall());
            announced.drafted(segment.id());
            return new Step.Done<>(reused);
        }
        return drafted(current, new Ready(item, mask, offer));
    }

    private void begun(final Current current, final WorkItem item) {
        events.started(item.segment(), current.work().position(item));
        announced.drafting(item.segment().id());
    }

    /** A segment that needs a call, with the mask it is shown and what the memory offered it. */
    private record Ready(WorkItem item, ProtectedMask mask, MemoryReuse.Offer offer) {}

    private Step<DraftOutcome> drafted(final Current current, final Ready ready) {
        final Segment segment = ready.item().segment();
        final ProtectedMask mask = ready.mask();
        final Optional<RunEnd> batchEnd = batches.ensure(current, ready.item(), mask, ready.offer());
        if (batchEnd.isPresent()) {
            return new Step.Stopped<>(batchEnd.get());
        }
        final List<PromptSection> batchSections = current.batches().takeSent(segment.id());
        final DraftOutcome batched = current.batches().take(segment.id());
        final Result<TranslateContext> shown = shownTo(current, ready, batched != null);
        final TranslateContext window = shown.data();
        if (window == null) {
            return new Step.Stopped<>(RoutedCalls.failedBy(Objects.requireNonNull(shown.error(), ERROR)));
        }
        final ContextSnapshot snapshot = snapshotOf(current, ready, window, batchSections);
        events.contextAssembled(segment.id(), snapshot);
        final Step<DraftOutcome> drafted =
                batched != null ? new Step.Done<>(batched) : draftAlone(current, ready, window);
        if (drafted instanceof Step.Done<DraftOutcome>(final DraftOutcome done)) {
            final DraftOutcome outcome = repairable(current, done, window, mask);
            current.drafts().drafted(outcome, snapshot);
            events.drafted(outcome, current.loop());
            announced.drafted(segment.id(), outcome);
            return new Step.Done<>(outcome);
        }
        return drafted;
    }

    // What the segment's record keeps of its call: the key to the run's fixed frame, stored once at run start, and the
    // user message's sections — the batch's as it sent them, or a single draft's as its plan has them.
    private ContextSnapshot snapshotOf(
            final Current current,
            final Ready ready,
            final TranslateContext window,
            @Nullable final List<PromptSection> batchSections) {
        final Segment segment = ready.item().segment();
        final String frameHash = steps.planner().frame().frameHash();
        if (batchSections != null) {
            log.debug("Snapshot of a batch draft segmentId={} sections={}", segment.id(), batchSections.size());
            return new ContextSnapshot(frameHash, PromptRequests.userSections(batchSections), List.of(), false);
        }
        final Result<List<PromptSection>> planned = current.requests().sentSections(segment, window, ready.mask());
        final List<PromptSection> sections = planned.data();
        if (sections == null) {
            log.warn(
                    "Sections of a single draft could not be planned segmentId={} code={}; stored without them",
                    segment.id(),
                    Objects.requireNonNull(planned.error(), ERROR).code());
            return new ContextSnapshot(frameHash, List.of(), List.of(), false);
        }
        log.debug("Snapshot of a single draft segmentId={} sections={}", segment.id(), sections.size());
        return new ContextSnapshot(frameHash, sections, List.of(), false);
    }

    // A segment no batch answered is drafted on its own; after a batch answer that copied an example, that draft is the
    // answer's one re-ask.
    private Step<DraftOutcome> draftAlone(final Current current, final Ready ready, final TranslateContext window) {
        final Segment segment = ready.item().segment();
        return calls.untilAnsweredOrFlagged(
                current.work(),
                segment.id(),
                new RoutedCalls.StepName("draft", segment.id()),
                () -> current.translator()
                        .translateSplit(
                                segment,
                                window,
                                ready.mask(),
                                steps.splitter(),
                                current.budget(),
                                current.batches().wasEchoed(segment.id())),
                error -> new DraftOutcome.FlaggedAtOnce(segment, segment.masked(), List.of(), error));
    }

    // A batch-adopted draft is repaired over its batch's window and map, its focus the one item; a single draft is
    // planned over its own window, read now from the segments accepted before it.
    private Result<TranslateContext> shownTo(final Current current, final Ready ready, final boolean batched) {
        final Segment segment = ready.item().segment();
        final TranslateContext fromBatch = batched ? current.batches().takeShown(segment.id()) : null;
        if (fromBatch != null) {
            return Result.ok(fromBatch);
        }
        return current.requests()
                .translateContext(
                        List.of(new PromptRequests.BatchSlot(
                                segment, ready.mask(), ready.offer().lookup())),
                        preceding.accepted(current.drafts()),
                        List.of());
    }

    // A batch-adopted draft and a single one alike carry the call that repairs them with the context and mask they
    // were drafted with, so the repair sees the names its draft saw.
    private static DraftOutcome repairable(
            final Current current,
            final DraftOutcome outcome,
            final TranslateContext context,
            final ProtectedMask mask) {
        if (outcome instanceof DraftOutcome.Drafted drafted && drafted.repairCall() == null) {
            log.trace("Repair call attached segmentId={}", drafted.segment().id());
            return drafted.withRepair(current.translator().repairCall(drafted.segment(), context, mask));
        }
        return outcome;
    }
}
