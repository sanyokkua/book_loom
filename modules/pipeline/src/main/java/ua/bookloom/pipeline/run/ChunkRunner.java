package ua.bookloom.pipeline.run;

import static ua.bookloom.util.contract.ArgNames.ERROR;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.index.BookIndex;

/**
 * Takes each unit's pending segments through chunks, in document order (design D4a). With the reviewer on, a chunk's
 * segments are all drafted before its reviewer call and only then decided one at a time, because the reviewer reads the
 * whole chunk; with it off each segment is drafted and decided before the next is drafted. The reviewer on or off is
 * this one branch, not a strategy. A segment whose context matches a memory entry that passes its checks takes the draft's
 * place and is decided in its turn with neither a draft nor the reviewer. What follows each decision — its deferrals, the
 * rolling summary and a unit's new names — is {@link DecisionFollowUp}'s, before the decision's pause boundary.
 *
 * <p>A chunk's drafts and its decider stay in memory across a pause, so resuming redoes only the call the pause
 * aborted — a draft, the reviewer, or the call inside a segment's rounds that failed. A step that pauses the run twice
 * for a provider error, or that the person skips from the pause, is flagged and the run goes on. A stop drops the
 * undecided drafts: those segments stay PENDING with no target, and the next run drafts them from the first pending
 * one.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class ChunkRunner {

    private final RunSteps steps;
    private final RunSettings settings;
    private final ChunkPlanner planner;
    private final RunStores stores;
    private final RunSinks sinks;
    private final RoutedCalls calls;
    private final DecisionFollowUp followUp;
    private final TermLearning learning;
    private final ChunkSteps announced;
    private final ChunkDrafting drafting;
    private final ChunkDeciding deciding;

    /**
     * Creates the runner of one run.
     *
     * @param steps the non-null calls a chunk is made of
     * @param settings the non-null brief and review mode of the run
     * @param window the non-null window latched as each chunk is entered, which later chunks are packed and sent for
     * @param stores the non-null stores the glossary and earlier decisions are read from
     * @param sinks the non-null places every decision goes
     * @param index the non-null index of the book the run started with, which a later close or import cannot change
     * @param document the non-null book the index was built from, whose segments are located
     */
    public ChunkRunner(
            final RunSteps steps,
            final RunSettings settings,
            final RunWindow window,
            final RunStores stores,
            final RunSinks sinks,
            final BookIndex index,
            final Document document) {
        this.steps = Objects.requireNonNull(steps, "steps");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.planner = new ChunkPlanner(settings, window);
        this.stores = Objects.requireNonNull(stores, "stores");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        final ChunkDrafting.Collaborators shared =
                ChunkDrafting.Collaborators.wire(steps, settings, stores, sinks, index, document);
        this.calls = shared.calls();
        this.announced = shared.announced();
        this.followUp = shared.followUp();
        this.learning = new TermLearning(
                stores,
                settings.projectId(),
                settings.frame().analysisLanguage(),
                settings.frame().targetLanguage());
        this.drafting = new ChunkDrafting(steps, shared);
        this.deciding = new ChunkDeciding(sinks, learning, shared);
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
        learning.replay(work.decidedSegments());
        while (work.hasPending()) {
            final Optional<RunEnd> end = runUnit(work, work.nextSection());
            if (end.isPresent()) {
                return end.get();
            }
            final Result<Integer> refreshed = work.refresh();
            if (refreshed.isErr()) {
                return RoutedCalls.failedBy(Objects.requireNonNull(refreshed.error(), ERROR));
            }
        }
        return new RunEnd(JobState.COMPLETED, null);
    }

    // The unit's glossary lines and summary are reserved as they stand when it is packed; a chunk re-reads the
    // glossary anyway, and a summary refreshed inside the unit is estimated when the next unit is packed.
    private Optional<RunEnd> runUnit(final WorkList work, final List<WorkItem> items) {
        final Result<List<GlossaryEntry>> glossary = stores.glossary().all(settings.projectId());
        if (glossary.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(glossary.error(), ERROR)));
        }
        announced.unitEntered();
        return runChunks(work, planner.walk(items));
    }

    private Optional<RunEnd> runChunks(final WorkList work, final ChunkPlanner.Walk walk) {
        while (walk.hasNext()) {
            final ChunkPlanner.Next next = walk.next();
            work.enterChunk(next.number(), next.total());
            announced.chunkEntered(next.items().size());
            final Optional<RunEnd> end = runChunk(work, next.chunk(), next.items(), next.number() - 1, next.budget());
            if (end.isPresent()) {
                return end;
            }
        }
        return Optional.empty();
    }

    private Optional<RunEnd> runChunk(
            final WorkList work, final Chunk chunk, final List<WorkItem> items, final int index, final int budget) {
        final Result<PromptRequests> read = PromptRequests.read(
                stores.glossary(),
                stores.lexicon(),
                planner.settings(),
                chunk,
                steps.gate(),
                steps.translator(),
                steps.batch(),
                steps.planner());
        if (read.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(read.error(), ERROR)));
        }
        final PromptRequests requests = Objects.requireNonNull(read.data(), "requests");
        final Current current = Current.of(work, items, requests, budget);
        final Optional<RunEnd> end = planner.settings().dial().hasReviewer()
                ? reviewedChunk(current, items)
                : unreviewedChunk(current, items);
        if (end.isPresent()) {
            final List<String> dropped = current.drafts().undecidedIds();
            log.debug("Stopped inside chunk={} droppedDrafts={} ids={}", index, dropped.size(), dropped);
            return end;
        }
        log.debug("Chunk decided chunk={} segments={}", index, items.size());
        return commitChunk(index, items.size());
    }

    // The reviewer call reads the whole chunk, so its lines name no segment; each draft's and decision's lines do.
    private Optional<RunEnd> reviewedChunk(final Current current, final List<WorkItem> items) {
        for (final WorkItem item : items) {
            final Step<DraftOutcome> drafted =
                    SegmentLogContext.within(item.segment().id(), () -> drafting.draft(current, item));
            if (drafted instanceof Step.Stopped<DraftOutcome>(final RunEnd end)) {
                return Optional.of(end);
            }
        }
        log.debug("Chunk drafted segments={}; reviewing it", items.size());
        return decideWith(current, null, items, current.drafts().all());
    }

    private Optional<RunEnd> unreviewedChunk(final Current current, final List<WorkItem> items) {
        log.debug("Chunk not reviewed segments={}: the dial turns the reviewer off", items.size());
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
        return switch (drafting.draft(current, item)) {
            case Step.Stopped<DraftOutcome>(final RunEnd stopped) -> Optional.of(stopped);
            case Step.Done<DraftOutcome>(final DraftOutcome outcome) ->
                decideWith(current, item.segment().id(), List.of(item), List.of(outcome));
        };
    }

    /**
     * Starts the quality loop over the outcomes — the reviewer call, when the dial enables it — then decides them.
     * {@code segmentId} is the one segment an unreviewed loop starts for, or {@code null} for a reviewed chunk.
     */
    private Optional<RunEnd> decideWith(
            final Current current,
            @Nullable final String segmentId,
            final List<WorkItem> items,
            final List<DraftOutcome> outcomes) {
        final Step<ChunkDecider> decider = calls.untilAnsweredOrFlagged(
                current.work(),
                segmentId,
                new RoutedCalls.StepName("review", items.getFirst().segment().id()),
                () -> steps.loop()
                        .start(outcomes, current.loop(), current.context().gate(), steps.calls()),
                error -> steps.loop()
                        .startWithoutReviewer(
                                outcomes, current.loop(), current.context().gate(), steps.calls(), error));
        return switch (decider) {
            case Step.Stopped<ChunkDecider>(final RunEnd end) -> Optional.of(end);
            case Step.Done<ChunkDecider>(final ChunkDecider started) -> deciding.decideEach(current, items, started);
        };
    }

    private Optional<RunEnd> commitChunk(final int index, final int decided) {
        final Result<Integer> committed = sinks.pending().flush();
        log.debug("Committed chunk={} decided={} ok={}", index, decided, committed.isOk());
        if (committed.isErr()) {
            return Optional.of(new RunEnd(JobState.FAILED, Objects.requireNonNull(committed.error(), ERROR)));
        }
        learning.committed();
        return Optional.empty();
    }
}
