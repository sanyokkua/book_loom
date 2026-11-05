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
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.pipeline.context.KindExtra;
import ua.bookloom.pipeline.context.PromptPlan;
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
    private final KeptTargets kept;
    private final MemoryReuse memory;
    private final RoutedCalls calls;
    private final SegmentEvents events;
    private final DraftShortcuts shortcuts;
    private final BatchStage batches;
    private final ChunkSteps announced;

    ChunkDrafting(final RunSteps steps, final Collaborators collaborators) {
        this.steps = steps;
        this.kept = collaborators.kept();
        this.memory = collaborators.memory();
        this.calls = collaborators.calls();
        this.events = collaborators.events();
        this.shortcuts = collaborators.shortcuts();
        this.batches = collaborators.batches();
        this.announced = collaborators.announced();
    }

    /** The collaborators a chunk's drafting and deciding share. */
    record Collaborators(
            KeptTargets kept,
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
                final KeptTargets kept,
                final RoutedCalls calls) {
            return new BatchStage(
                    steps.batch(),
                    settings,
                    memory,
                    kept,
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
            final KeptTargets kept = new KeptTargets(stores.segments(), settings.projectId(), sinks.pending());
            final MemoryReuse memory =
                    new MemoryReuse(new TranslationMemory(stores.tm(), settings.projectId()), locators);
            final RoutedCalls calls = new RoutedCalls(sinks.boundaries());
            final ChunkSteps announced = new ChunkSteps(steps.calls().steps(), settings.dial());
            final DecisionFollowUp followUp = new DecisionFollowUp(
                    new DecisionFollowUp.BookFacts(
                            settings.projectId(), settings.frame().analysisLanguage(), index),
                    stores,
                    sinks);
            return new Collaborators(
                    kept,
                    memory,
                    calls,
                    followUp,
                    new SegmentEvents(sinks.emit(), locators, steps.calls()),
                    DraftShortcuts.forFrame(settings.frame()),
                    batchStageOf(steps, settings, memory, kept, calls),
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
        final ProtectedMask mask = current.inputs().mask(segment);
        final DraftOutcome shortcut =
                shortcuts.take(segment, mask, current.inputs().gate(), current.loop(), current.drafts());
        if (shortcut != null) {
            events.drafted(shortcut, current.loop());
            announced.drafted(segment.id());
            return new Step.Done<>(shortcut);
        }
        final List<Segment> unitSegments = current.work().unitSegments(item);
        final MemoryReuse.Offer offer =
                memory.offer(segment, unitSegments, mask, current.inputs().gate(), current.loop());
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
        final PromptPlan batchPlan = current.batches().takeSent(segment.id());
        final DraftOutcome batched = current.batches().take(segment.id());
        final Result<TranslateContext> shown = shownTo(current, ready, batched != null);
        final TranslateContext window = shown.data();
        if (window == null) {
            return new Step.Stopped<>(RoutedCalls.failedBy(Objects.requireNonNull(shown.error(), ERROR)));
        }
        final ContextSnapshot snapshot = snapshotOf(current, ready, window, batchPlan);
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

    // What the segment's record keeps of its call: the key to the run's fixed frame, stored once at run start, the user
    // message's sections and the keys of the summaries block 8 carried — the batch's as it sent them, or a single
    // draft's as its plan has them.
    private ContextSnapshot snapshotOf(
            final Current current,
            final Ready ready,
            final TranslateContext window,
            @Nullable final PromptPlan batchPlan) {
        final Segment segment = ready.item().segment();
        final String frameHash = steps.planner().frame().frameHash();
        if (batchPlan != null) {
            log.debug(
                    "Snapshot of a batch draft segmentId={} blocks={} summaryKeys={}",
                    segment.id(),
                    batchPlan.blocks().size(),
                    batchPlan.chapterSummaryKeys());
            return snapshot(frameHash, batchPlan);
        }
        final Result<PromptPlan> planned = current.requests().sentPlan(segment, window, ready.mask(), KindExtra.NONE);
        final PromptPlan plan = planned.data();
        if (plan == null) {
            log.warn(
                    "Sections of a single draft could not be planned segmentId={} code={}; stored without them",
                    segment.id(),
                    Objects.requireNonNull(planned.error(), ERROR).code());
            return new ContextSnapshot(frameHash, List.of(), List.of(), false);
        }
        log.debug(
                "Snapshot of a single draft segmentId={} blocks={} summaryKeys={}",
                segment.id(),
                plan.blocks().size(),
                plan.chapterSummaryKeys());
        return snapshot(frameHash, plan);
    }

    private static ContextSnapshot snapshot(final String frameHash, final PromptPlan plan) {
        return new ContextSnapshot(
                frameHash, PromptRequests.userSections(plan.sections()), plan.chapterSummaryKeys(), false);
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
                        kept.accepted(current.drafts()),
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
