package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.chunk.TokenBudget;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextPackage;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * Takes each unit's pending segments through chunks, in document order (design D4a). With the judge on, a chunk's
 * segments are all drafted before its one judge call and only then decided one at a time, because the judge reads the
 * whole chunk; with it off each segment is drafted and decided before the next is drafted. The judge on or off is this
 * one branch, not a strategy.
 *
 * <p>A chunk's drafts and its decider stay in memory across a pause, so resuming redoes only the call the pause
 * aborted — a draft, the judge, or a segment's rounds from the first. A stop drops the undecided drafts: those segments
 * stay PENDING with no target, and the next run drafts them from the first pending one.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class ChunkRunner {

    // The style sheet is English prose, as every prompt is.
    private static final String PROMPT_LANGUAGE = "en";

    private final RunSteps steps;
    private final RunSettings settings;
    private final RunStores stores;
    private final RunSinks sinks;
    private final PrecedingTargets preceding;
    private final int fixedHeadroom;

    /**
     * Creates the runner of one run.
     *
     * @param steps the non-null calls a chunk is made of
     * @param settings the non-null brief and review mode of the run
     * @param stores the non-null stores the glossary and earlier decisions are read from
     * @param sinks the non-null places every decision goes
     */
    public ChunkRunner(final RunSteps steps, final RunSettings settings, final RunStores stores, final RunSinks sinks) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.stores = Objects.requireNonNull(stores, "stores");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.preceding = new PrecedingTargets(stores.segments(), settings.projectId(), sinks.pending());
        final CallFrame frame = settings.frame();
        this.fixedHeadroom = TokenEstimator.estimate(frame.styleSheet().text(), PROMPT_LANGUAGE)
                + TokenBudget.fullChunkAllowance(frame.sourceLanguage(), frame.targetLanguage());
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
        while (work.hasPending()) {
            final Optional<RunEnd> end = runUnit(work, work.nextSection());
            if (end.isPresent()) {
                return end.get();
            }
            final Result<Integer> refreshed = work.refresh();
            if (refreshed.isErr()) {
                return failedBy(Objects.requireNonNull(refreshed.error(), "error"));
            }
        }
        return new RunEnd(JobState.COMPLETED, null);
    }

    // The unit's glossary lines are reserved as they stand when it is packed; a chunk re-reads the glossary anyway.
    private Optional<RunEnd> runUnit(final WorkList work, final List<WorkItem> items) {
        final List<Segment> segments = items.stream().map(WorkItem::segment).toList();
        final Result<List<GlossaryEntry>> glossary = stores.glossary().all(settings.projectId());
        if (glossary.isErr()) {
            return Optional.of(failedBy(Objects.requireNonNull(glossary.error(), "error")));
        }
        final int headroom = fixedHeadroom
                + ChunkContext.termsEstimate(segments, Objects.requireNonNull(glossary.data(), "glossary"));
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
        int offset = 0;
        for (int index = 0; index < chunks.size(); index++) {
            final int size = chunks.get(index).segments().size();
            final List<WorkItem> chunkItems = items.subList(offset, offset + size);
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
            return Optional.of(failedBy(Objects.requireNonNull(read.error(), "error")));
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

    private Optional<RunEnd> judgedChunk(final Current current, final List<WorkItem> items) {
        for (final WorkItem item : items) {
            if (draft(current, item) instanceof Step.Stopped<DraftOutcome>(final RunEnd end)) {
                return Optional.of(end);
            }
        }
        log.debug("Chunk drafted segments={}; judging it once", items.size());
        return decideWith(current, items, current.drafts().all());
    }

    private Optional<RunEnd> unjudgedChunk(final Current current, final List<WorkItem> items) {
        log.debug("Chunk not judged segments={}: the dial turns the judge off", items.size());
        for (final WorkItem item : items) {
            final Optional<RunEnd> end =
                    switch (draft(current, item)) {
                        case Step.Stopped<DraftOutcome>(final RunEnd stopped) -> Optional.of(stopped);
                        case Step.Done<DraftOutcome>(final DraftOutcome outcome) ->
                            decideWith(current, List.of(item), List.of(outcome));
                    };
            if (end.isPresent()) {
                return end;
            }
        }
        return Optional.empty();
    }

    /** Starts the quality loop over the outcomes — the judge call, when the dial enables it — then decides them. */
    private Optional<RunEnd> decideWith(
            final Current current, final List<WorkItem> items, final List<DraftOutcome> outcomes) {
        final Step<ChunkDecider> decider = untilAnswered(
                current.work(),
                null,
                () -> steps.loop()
                        .start(outcomes, current.loop(), current.context().gate(), steps.calls()));
        return switch (decider) {
            case Step.Stopped<ChunkDecider>(final RunEnd end) -> Optional.of(end);
            case Step.Done<ChunkDecider>(final ChunkDecider started) -> decideEach(current, items, started);
        };
    }

    /** Drafts one segment behind its protected spans and with its context package, keeping the draft in the chunk. */
    private Step<DraftOutcome> draft(final Current current, final WorkItem item) {
        final Segment segment = item.segment();
        final Result<List<String>> earlierMaskedTargets = preceding.earlierMaskedTargets(
                current.work().unitSegments(item), segment, settings.dial().precedingTargets(), current.drafts());
        if (earlierMaskedTargets.isErr()) {
            return new Step.Stopped<>(failedBy(Objects.requireNonNull(earlierMaskedTargets.error(), "error")));
        }
        final ContextPackage context =
                current.context().contextFor(segment, Objects.requireNonNull(earlierMaskedTargets.data(), "targets"));
        if (log.isTraceEnabled()) {
            log.trace(
                    "Preceding targets segmentId={} targets={}",
                    segment.id(),
                    context.draftContext().precedingTargets());
        }
        final ProtectedMask mask = current.context().mask(segment);
        final Step<DraftOutcome> drafted = untilAnswered(
                current.work(),
                segment.id(),
                () -> current.translator()
                        .translateSplit(segment, context.draftContext(), mask, steps.splitter(), current.budget()));
        if (drafted instanceof Step.Done<DraftOutcome>(final DraftOutcome outcome)) {
            current.drafts().drafted(outcome, context.snapshot());
        }
        return drafted;
    }

    private Optional<RunEnd> decideEach(final Current current, final List<WorkItem> items, final ChunkDecider decider) {
        for (final WorkItem item : items) {
            final Step<SegmentOutcome> decided =
                    untilAnswered(current.work(), item.segment().id(), decider::nextDecision);
            final Optional<RunEnd> end =
                    switch (decided) {
                        case Step.Stopped<SegmentOutcome>(final RunEnd stopped) -> Optional.of(stopped);
                        case Step.Done<SegmentOutcome>(final SegmentOutcome outcome) -> record(current, item, outcome);
                    };
            if (end.isPresent()) {
                return end;
            }
        }
        return Optional.empty();
    }

    private Optional<RunEnd> record(final Current current, final WorkItem item, final SegmentOutcome outcome) {
        final SegmentRecord record = OutcomeRecords.decided(
                item.record(), outcome, current.drafts().snapshot(item.segment().id()));
        sinks.pending().decided(record);
        current.drafts().decided(record.segmentId());
        sinks.recorder().decided(record.status());
        final JobProgress progress = current.work().apply(item, record.status());
        final ErrorCode reason = record.status() == SegmentStatus.FLAGGED ? OutcomeRecords.reportCode(record) : null;
        log.debug(
                "Decided segmentId={} status={} reason={} path={} rounds={}",
                record.segmentId(),
                record.status(),
                reason,
                record.path(),
                record.repairRounds());
        sinks.emit().accept(new SegmentDecided(record.segmentId(), record.status(), reason, progress));
        return sinks.boundaries()
                .afterDecision(current.work().endsSection(item), current.work().isComplete(), progress);
    }

    private Optional<RunEnd> commitChunk(final int index, final int decided) {
        final Result<Integer> committed = sinks.pending().flush();
        log.debug("Committed chunk={} decided={} ok={}", index, decided, committed.isOk());
        return committed.isErr()
                ? Optional.of(new RunEnd(JobState.FAILED, Objects.requireNonNull(committed.error(), "error")))
                : Optional.empty();
    }

    /**
     * Makes a call until it answers, routing each error it answers by design D3: a stop or a pause aborting it, and a
     * provider error the run pauses on, are answered by the job's boundaries, which either end the run or send the
     * call again once the person resumes.
     */
    private <T> Step<T> untilAnswered(
            final WorkList work, @Nullable final String segmentId, final Supplier<Result<T>> call) {
        while (true) {
            final Result<T> result = withSegment(segmentId, call);
            if (result.isOk()) {
                return new Step.Done<>(Objects.requireNonNull(result.data(), "data"));
            }
            final AppError error = Objects.requireNonNull(result.error(), "error");
            final Optional<RunEnd> end = afterError(error, work.currentTranslationProgress());
            if (end.isPresent()) {
                return new Step.Stopped<>(end.get());
            }
            log.debug("Making the call again segmentId={} after code={}", segmentId, error.code());
        }
    }

    private Optional<RunEnd> afterError(final AppError error, final JobProgress progress) {
        return switch (PauseDecider.route(error.code())) {
            case CANCELLED -> sinks.boundaries().afterAbortedCall(progress);
            case PAUSE_OR_FAIL -> sinks.boundaries().afterRoutedError(error, progress);
            case FLAG_AT_ONCE, FAIL -> Optional.of(failedBy(endingError(error)));
        };
    }

    private static <T> Result<T> withSegment(@Nullable final String segmentId, final Supplier<Result<T>> call) {
        if (segmentId != null) {
            MDC.put("segment", segmentId);
        }
        try {
            return Objects.requireNonNull(call.get(), "call result");
        } finally {
            MDC.remove("segment");
        }
    }

    // A model error the run cannot recover from is an application fault, never a provider error that Retry now
    // could fix, so it ends the run as internal. An internal error was already logged where it was built.
    private static AppError endingError(final AppError error) {
        if (error.code() == ErrorCode.internal) {
            return error;
        }
        final IllegalStateException cause = new IllegalStateException(
                "A model call answered " + error.code() + ", which a run cannot recover from");
        log.error("Translation job stopped by a model error it cannot recover from code={}", error.code(), cause);
        return AppError.of(
                ErrorCode.internal,
                "Translation job failed",
                "An unexpected failure stopped this translation job.",
                null,
                cause);
    }

    private static RunEnd failedBy(final AppError error) {
        return new RunEnd(JobState.FAILED, error);
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

    /** A call's answer once the run went on, or how the run ended while it waited for one. */
    private sealed interface Step<T> {

        record Done<T>(T value) implements Step<T> {}

        record Stopped<T>(RunEnd end) implements Step<T> {}
    }
}
