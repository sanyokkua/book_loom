package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentLocator;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.TranslationMemory;

/**
 * Takes each unit's pending segments through chunks, in document order (design D4a). With the judge on, a chunk's
 * segments are all drafted before its one judge call and only then decided one at a time, because the judge reads the
 * whole chunk; with it off each segment is drafted and decided before the next is drafted. The judge on or off is this
 * one branch, not a strategy. A segment whose context matches a memory entry that passes its checks takes the draft's
 * place and is decided in its turn with neither a draft nor the judge. What follows each decision — its deferrals, the
 * rolling summary and a unit's new names — is {@link DecisionFollowUp}'s, before the decision's pause boundary.
 *
 * <p>A chunk's drafts and its decider stay in memory across a pause, so resuming redoes only the call the pause
 * aborted — a draft, the judge, or a segment's rounds from the first. A stop drops the undecided drafts: those segments
 * stay PENDING with no target, and the next run drafts them from the first pending one.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class ChunkRunner {

    private final RunSteps steps;
    private final RunSettings settings;
    private final RunStores stores;
    private final RunSinks sinks;
    private final PrecedingTargets preceding;
    private final MemoryReuse memory;
    private final RoutedCalls calls;
    private final DecisionFollowUp followUp;
    private final SegmentEvents events;

    /**
     * Creates the runner of one run.
     *
     * @param steps the non-null calls a chunk is made of
     * @param settings the non-null brief and review mode of the run
     * @param stores the non-null stores the glossary and earlier decisions are read from
     * @param sinks the non-null places every decision goes
     * @param locators the non-null locator of every segment of the opened book
     */
    public ChunkRunner(
            final RunSteps steps,
            final RunSettings settings,
            final RunStores stores,
            final RunSinks sinks,
            final Map<String, SegmentLocator> locators) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.preceding = new PrecedingTargets(stores.segments(), settings.projectId(), sinks.pending());
        this.memory = new MemoryReuse(new TranslationMemory(stores.tm(), settings.projectId()), locators);
        this.calls = new RoutedCalls(sinks.boundaries());
        this.followUp = new DecisionFollowUp(settings.projectId(), steps.summary(), stores, sinks, calls);
        this.events = new SegmentEvents(sinks.emit(), locators);
    }

    /**
     * Decides every segment still pending, unit by unit and chunk by chunk, reading the brief's switches again as each
     * unit ends.
     *
     * @param work the non-null list of the segments to decide
     * @return completed once every segment is decided, or how a boundary or a failure ended the run
     */
    public RunEnd run(final WorkList work) {
        Objects.requireNonNull(work, "work");
        final Optional<RunEnd> unread = followUp.readSummary();
        if (unread.isPresent()) {
            return unread.get();
        }
        while (work.hasPending()) {
            final Optional<RunEnd> end = runUnit(work, work.nextSection());
            if (end.isPresent()) {
                return end.get();
            }
            final Result<Integer> refreshed = work.refresh();
            if (refreshed.isErr()) {
                return RoutedCalls.failedBy(Objects.requireNonNull(refreshed.error(), "error"));
            }
        }
        return new RunEnd(JobState.COMPLETED, null);
    }

    // The unit's glossary lines and summary are reserved as they stand when it is packed; a chunk re-reads the
    // glossary anyway, and a summary refreshed inside the unit is estimated when the next unit is packed.
    private Optional<RunEnd> runUnit(final WorkList work, final List<WorkItem> items) {
        final List<Segment> segments = items.stream().map(WorkItem::segment).toList();
        final Result<List<GlossaryEntry>> glossary = stores.glossary().all(settings.projectId());
        if (glossary.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(glossary.error(), "error")));
        }
        final int headroom = ChunkBudget.headroom(
                settings.frame(), segments, Objects.requireNonNull(glossary.data(), "glossary"), followUp.summary());
        final int budget = TokenBudget.chunkTokens(headroom);
        final int cap = settings.dial().chunkCap(settings.mode());
        final List<Chunk> chunks = ChunkPacker.pack(segments, settings.frame().sourceLanguage(), budget, cap);
        log.debug(
                "Packed unit unitId={} budget={} headroom={} cap={} chunkSizes={}",
                segments.getFirst().unit(),
                budget,
                headroom,
                cap,
                chunks.stream().map(chunk -> chunk.segments().size()).toList());
        return runChunks(work, items, chunks, budget);
    }

    private Optional<RunEnd> runChunks(
            final WorkList work, final List<WorkItem> items, final List<Chunk> chunks, final int budget) {
        int offset = 0;
        for (int index = 0; index < chunks.size(); index++) {
            final int size = chunks.get(index).segments().size();
            final List<WorkItem> chunkItems = items.subList(offset, offset + size);
            work.enterChunk(index + 1, chunks.size());
            final Optional<RunEnd> end = runChunk(work, chunks.get(index), chunkItems, index, budget);
            if (end.isPresent()) {
                return end;
            }
            offset += size;
        }
        return Optional.empty();
    }

    private Optional<RunEnd> runChunk(
            final WorkList work, final Chunk chunk, final List<WorkItem> items, final int index, final int budget) {
        final Result<ChunkContext> read = ChunkContext.read(stores.glossary(), settings, chunk, steps.gate());
        if (read.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(read.error(), "error")));
        }
        final ChunkContext context = Objects.requireNonNull(read.data(), "context");
        final Current current = new Current(
                work,
                new ChunkDrafts(),
                context,
                new LoopSettings(settings.mode(), settings.dial(), settings.frame(), settings.names(), context.terms()),
                steps.translator().gatedBy(context.gate()),
                budget);
        final Optional<RunEnd> end =
                settings.dial().judge() ? judgedChunk(current, items) : unjudgedChunk(current, items);
        if (end.isPresent()) {
            final List<String> dropped = current.drafts().undecidedIds();
            log.debug("Stopped inside chunk={} droppedDrafts={} ids={}", index, dropped.size(), dropped);
            return end;
        }
        log.debug("Chunk decided chunk={} segments={}", index, items.size());
        return commitChunk(index, items.size());
    }

    // The judge call reads the whole chunk, so its lines name no segment; each draft's and decision's lines do.
    private Optional<RunEnd> judgedChunk(final Current current, final List<WorkItem> items) {
        for (final WorkItem item : items) {
            final Step<DraftOutcome> drafted =
                    SegmentLogContext.within(item.segment().id(), () -> draft(current, item));
            if (drafted instanceof Step.Stopped<DraftOutcome>(final RunEnd end)) {
                return Optional.of(end);
            }
        }
        log.debug("Chunk drafted segments={}; judging it once", items.size());
        return decideWith(current, null, items, current.drafts().all());
    }

    private Optional<RunEnd> unjudgedChunk(final Current current, final List<WorkItem> items) {
        log.debug("Chunk not judged segments={}: the dial turns the judge off", items.size());
        for (final WorkItem item : items) {
            final Optional<RunEnd> end =
                    SegmentLogContext.within(item.segment().id(), () -> draftAndDecide(current, item));
            if (end.isPresent()) {
                return end;
            }
        }
        return Optional.empty();
    }

    private Optional<RunEnd> draftAndDecide(final Current current, final WorkItem item) {
        return switch (draft(current, item)) {
            case Step.Stopped<DraftOutcome>(final RunEnd stopped) -> Optional.of(stopped);
            case Step.Done<DraftOutcome>(final DraftOutcome outcome) ->
                decideWith(current, item.segment().id(), List.of(item), List.of(outcome));
        };
    }

    /**
     * Starts the quality loop over the outcomes — the judge call, when the dial enables it — then decides them.
     * {@code segmentId} is the one segment an unjudged loop starts for, or {@code null} for a judged chunk.
     */
    private Optional<RunEnd> decideWith(
            final Current current,
            @Nullable final String segmentId,
            final List<WorkItem> items,
            final List<DraftOutcome> outcomes) {
        final Step<ChunkDecider> decider = calls.untilAnswered(
                current.work(),
                segmentId,
                () -> steps.loop()
                        .start(outcomes, current.loop(), current.context().gate(), steps.calls()));
        return switch (decider) {
            case Step.Stopped<ChunkDecider>(final RunEnd end) -> Optional.of(end);
            case Step.Done<ChunkDecider>(final ChunkDecider started) -> decideEach(current, items, started);
        };
    }

    /**
     * Drafts one segment behind its protected spans and with its context package, keeping the draft in the chunk —
     * unless a context-matched memory target passes its checks, which stands in for the draft.
     */
    private Step<DraftOutcome> draft(final Current current, final WorkItem item) {
        final Segment segment = item.segment();
        events.started(segment, current.work().position(item));
        final List<Segment> unitSegments = current.work().unitSegments(item);
        final Result<List<String>> earlierMaskedTargets = preceding.earlierMaskedTargets(
                unitSegments, segment, settings.dial().precedingTargets(), current.drafts());
        if (earlierMaskedTargets.isErr()) {
            return new Step.Stopped<>(
                    RoutedCalls.failedBy(Objects.requireNonNull(earlierMaskedTargets.error(), "error")));
        }
        final ProtectedMask mask = current.context().mask(segment);
        final MemoryReuse.Offer offer =
                memory.offer(segment, unitSegments, mask, current.context().gate(), current.loop());
        final ContextPackage context =
                contextOf(current, segment, Objects.requireNonNull(earlierMaskedTargets.data(), "targets"), offer);
        final DraftOutcome.Reused reused = offer.reused();
        if (reused != null) {
            current.drafts().drafted(reused, context.snapshot());
            return new Step.Done<>(reused);
        }
        return drafted(current, segment, context, mask);
    }

    private Step<DraftOutcome> drafted(
            final Current current, final Segment segment, final ContextPackage context, final ProtectedMask mask) {
        final Step<DraftOutcome> drafted = calls.untilAnswered(
                current.work(),
                segment.id(),
                () -> current.translator()
                        .translateSplit(segment, context.draftContext(), mask, steps.splitter(), current.budget()));
        if (drafted instanceof Step.Done<DraftOutcome>(final DraftOutcome outcome)) {
            current.drafts().drafted(outcome, context.snapshot());
            events.drafted(outcome, current.loop());
        }
        return drafted;
    }

    private ContextPackage contextOf(
            final Current current, final Segment segment, final List<String> targets, final MemoryReuse.Offer offer) {
        final ContextPackage context =
                current.context().contextFor(segment, targets, offer.lookup(), followUp.summary());
        if (log.isTraceEnabled()) {
            log.trace(
                    "Preceding targets segmentId={} targets={}",
                    segment.id(),
                    context.draftContext().precedingTargets());
        }
        return context;
    }

    private Optional<RunEnd> decideEach(final Current current, final List<WorkItem> items, final ChunkDecider decider) {
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
        return switch (calls.untilAnswered(current.work(), item.segment().id(), decider::nextDecision)) {
            case Step.Stopped<SegmentOutcome>(final RunEnd stopped) -> Optional.of(stopped);
            case Step.Done<SegmentOutcome>(final SegmentOutcome outcome) -> record(current, item, outcome, decider);
        };
    }

    private Optional<RunEnd> record(
            final Current current, final WorkItem item, final SegmentOutcome outcome, final ChunkDecider decider) {
        final SegmentRecord record = OutcomeRecords.decided(
                item.record(), outcome, current.drafts().snapshot(item.segment().id()));
        sinks.pending()
                .decided(
                        record,
                        memory.entryFor(record, item.segment(), current.work().unitSegments(item)));
        current.drafts().decided(record.segmentId());
        sinks.recorder().decided(record.status());
        final JobProgress progress = current.work().apply(item, record.status(), record.path());
        final ErrorCode reason = record.status() == SegmentStatus.FLAGGED ? OutcomeRecords.reportCode(record) : null;
        log.debug(
                "Decided segmentId={} status={} reason={} path={} rounds={}",
                record.segmentId(),
                record.status(),
                reason,
                record.path(),
                record.repairRounds());
        events.decided(record, reason, progress);
        if (record.path() == SegmentPath.TM_REUSE) {
            sinks.emit().accept(memory.announced(record.segmentId()));
        }
        return followUp.decided(current.work(), item, record, current.context().glossary(), decider.deferrals())
                .or(() -> sinks.boundaries().afterDecision(boundaryOf(current.work(), item, record), progress));
    }

    private static RunBoundaries.Decision boundaryOf(
            final WorkList work, final WorkItem item, final SegmentRecord record) {
        return new RunBoundaries.Decision(
                record.segmentId(),
                record.status() == SegmentStatus.FLAGGED,
                work.endsSection(item),
                work.isComplete());
    }

    private Optional<RunEnd> commitChunk(final int index, final int decided) {
        final Result<Integer> committed = sinks.pending().flush();
        log.debug("Committed chunk={} decided={} ok={}", index, decided, committed.isOk());
        return committed.isErr()
                ? Optional.of(new RunEnd(JobState.FAILED, Objects.requireNonNull(committed.error(), "error")))
                : Optional.empty();
    }

    /**
     * The chunk being decided: the unit's work, its drafts, the glossary it read, its loop settings, the draft step
     * gated through its protected spans, and the unit's budget an oversized segment is split against.
     */
    private record Current(
            WorkList work,
            ChunkDrafts drafts,
            ChunkContext context,
            LoopSettings loop,
            SegmentTranslator translator,
            int budget) {}
}
