package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchContexts;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.ItemOutcome;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.VerbatimCheck;
import ua.bookloom.pipeline.lexicon.TermMappingVerifier;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * Drafts the segments of a chunk that need a model call in batches, ahead of their turn: when the chunk reaches a
 * segment that needs a call and no batch has dealt with it, the segment and the next ones that need a call go to the
 * model together, by token budget and the adaptive size, and the answers that came back whole wait in
 * {@link ChunkBatches}. A segment the batch did not answer correctly is simply not waiting there, so it takes the
 * ordinary single-segment draft at its turn; nothing else about the chunk changes. Segments a shortcut decides — kept
 * as they are, an auxiliary text, a translation-memory reuse — and a segment too large for the budget never join a
 * batch.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class BatchStage {

    // The previous pairs a batch shows; the dial's own count when it is larger.
    private static final int MIN_PAIRS = 2;
    private static final int MAX_PAIRS = 3;

    private final BatchDrafter drafter;
    private final RunSettings settings;
    private final MemoryReuse memory;
    private final PrecedingTargets preceding;
    private final RoutedCalls calls;
    private final Supplier<@Nullable String> summary;

    /**
     * Creates the batch side of one run.
     *
     * @param drafter the non-null drafter, whose size adapts over the run
     * @param settings the non-null run settings
     * @param memory the non-null memory side of the run, whose lookups decide which segments a reuse takes
     * @param preceding the non-null reader of the chapter's earlier targets
     * @param calls the non-null router a batch call goes through
     * @param summary the rolling summary as it stands, or null while there is none
     */
    BatchStage(
            final BatchDrafter drafter,
            final RunSettings settings,
            final MemoryReuse memory,
            final PrecedingTargets preceding,
            final RoutedCalls calls,
            final Supplier<@Nullable String> summary) {
        this.drafter = Objects.requireNonNull(drafter, "drafter");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.preceding = Objects.requireNonNull(preceding, "preceding");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    /** A segment that will be drafted by a call, with what the batch needs of it. */
    private record Candidate(WorkItem item, ProtectedMask mask, MemoryReuse.Offer offer) {

        Segment segment() {
            return item.segment();
        }
    }

    /**
     * Makes the batch that starts at a segment about to be drafted, unless a batch has dealt with it or fewer than two
     * segments would go in it.
     *
     * @param current the chunk being drafted
     * @param first the segment about to be drafted, which needs a call
     * @param mask its protected mask
     * @param offer what the memory offered it, with no reuse
     * @return how the run ended while the batch call was routed, or empty when the chunk goes on
     */
    Optional<RunEnd> ensure(
            final Current current, final WorkItem first, final ProtectedMask mask, final MemoryReuse.Offer offer) {
        final String firstId = first.segment().id();
        if (current.batches().hasTried(firstId)) {
            return Optional.empty();
        }
        final List<Candidate> batch = gather(current, new Candidate(first, mask, offer));
        if (batch.size() < 2) {
            log.debug("No batch segmentId={} candidates={}: drafted on its own", firstId, batch.size());
            current.batches().tried(firstId);
            return Optional.empty();
        }
        return send(current, batch);
    }

    private List<Candidate> gather(final Current current, final Candidate first) {
        final int budget = current.budget();
        int tokens = cost(first);
        if (tokens > budget) {
            log.debug(
                    "Segment {} is above the chunk budget={}: drafted on its own",
                    first.segment().id(),
                    budget);
            return List.of();
        }
        final List<Candidate> batch = new ArrayList<>(List.of(first));
        final List<WorkItem> items = current.items();
        for (int i = items.indexOf(first.item()) + 1; i < items.size() && batch.size() < drafter.size(); i++) {
            final Candidate next = candidateOf(current, items.get(i));
            if (next == null) {
                continue;
            }
            if (tokens + cost(next) > budget) {
                break;
            }
            tokens += cost(next);
            batch.add(next);
        }
        return batch;
    }

    private int cost(final Candidate candidate) {
        return TokenEstimator.estimate(
                candidate.mask().maskedText(), settings.frame().sourceLanguage());
    }

    // A later segment joins when a call is all that would decide it: no shortcut, no memory reuse, not already tried.
    private @Nullable Candidate candidateOf(final Current current, final WorkItem item) {
        final Segment segment = item.segment();
        if (current.batches().hasTried(segment.id()) || Unit.AUXILIARY_ID.equals(segment.unit())) {
            return null;
        }
        final ProtectedMask mask = current.context().mask(segment);
        if (VerbatimCheck.check(
                        segment,
                        mask.maskedText(),
                        mask.presentLocked(),
                        current.context().gate())
                != null) {
            return null;
        }
        final MemoryReuse.Offer offer = memory.offer(
                segment,
                current.work().unitSegments(item),
                mask,
                current.context().gate(),
                current.loop());
        return offer.reused() == null ? new Candidate(item, mask, offer) : null;
    }

    private Optional<RunEnd> send(final Current current, final List<Candidate> batch) {
        final List<BatchItem> items = itemsOf(batch);
        final List<String> segmentIds =
                batch.stream().map(candidate -> candidate.segment().id()).toList();
        final List<String> keyTerms = current.context()
                .keyTermsIn(batch.stream().map(Candidate::segment).toList());
        final Result<BatchContext> context = contextOf(current, batch, items, keyTerms);
        if (context.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(context.error(), "error")));
        }
        final List<String> ids = items.stream().map(BatchItem::id).toList();
        log.debug(
                "Drafting a batch of {} segments from segmentId={} ids={}",
                batch.size(),
                segmentIds.getFirst(),
                segmentIds);
        final Step<BatchReply> step = calls.untilAnsweredOrFlagged(
                current.work(),
                null,
                new RoutedCalls.StepName("batch", segmentIds.getFirst()),
                () -> attempt(Objects.requireNonNull(context.data(), "context"), items, segmentIds),
                error -> BatchReply.unreadable(ids));
        return switch (step) {
            case Step.Stopped<BatchReply>(final RunEnd end) -> Optional.of(end);
            case Step.Done<BatchReply>(final BatchReply reply) -> {
                settle(current, batch, reply, keyTerms);
                yield Optional.empty();
            }
        };
    }

    private static List<BatchItem> itemsOf(final List<Candidate> batch) {
        return IntStream.range(0, batch.size())
                .mapToObj(i -> new BatchItem(
                        Integer.toString(i + 1), batch.get(i).mask().maskedText()))
                .toList();
    }

    // An answer the draft step would flag its one segment for (an empty completion, a context-window error) says
    // nothing about the others: the batch is unreadable and every segment takes its own draft, which flags itself.
    private Result<BatchReply> attempt(
            final BatchContext context, final List<BatchItem> items, final List<String> segmentIds) {
        final Result<BatchReply> answered = drafter.draft(context, items, segmentIds);
        final AppError error = answered.error();
        if (error != null && PauseDecider.route(error.code()) == PauseDecider.Route.FLAG_AT_ONCE) {
            log.warn("The batch call answered code={}; every segment is drafted on its own", error.code());
            return Result.ok(
                    BatchReply.unreadable(items.stream().map(BatchItem::id).toList()));
        }
        return answered;
    }

    // Every segment the batch covered is tried; only the ones the model answered whole and the gate accepted wait for
    // their turn, the rest take their own call there.
    private void settle(
            final Current current, final List<Candidate> batch, final BatchReply reply, final List<String> keyTerms) {
        int adopted = 0;
        for (int i = 0; i < batch.size(); i++) {
            final Candidate candidate = batch.get(i);
            current.batches().tried(candidate.segment().id());
            final ItemOutcome outcome = reply.outcome(Integer.toString(i + 1)).orElseThrow();
            if (outcome.isAccepted() && adopt(current, candidate, outcome)) {
                adopted++;
                recordTerms(current, candidate, outcome, keyTerms);
            } else {
                log.warn(
                        "Batch item falls back to its own draft segmentId={} status={} problems={}",
                        candidate.segment().id(),
                        outcome.status(),
                        outcome.problems());
            }
        }
        drafter.record(reply);
        log.info(
                "Batch answered segments={} adopted={} fallbacks={} nextSize={}",
                batch.size(),
                adopted,
                batch.size() - adopted,
                drafter.size());
    }

    private static boolean adopt(final Current current, final Candidate candidate, final ItemOutcome outcome) {
        final Optional<DraftOutcome> drafted =
                current.translator().adopt(candidate.segment(), candidate.mask(), outcome.target());
        drafted.ifPresent(found -> current.batches().ready(candidate.segment().id(), found));
        return drafted.isPresent();
    }

    // What the model says it used for a key term counts only once the text proves it; the lexicon then holds one more
    // use.
    private void recordTerms(
            final Current current, final Candidate candidate, final ItemOutcome outcome, final List<String> keyTerms) {
        if (outcome.terms().isEmpty()) {
            return;
        }
        final List<TermMappingVerifier.Pair> verified = TermMappingVerifier.verify(
                outcome.terms(),
                keyTerms,
                Tokens.replace(candidate.mask().maskedText(), " "),
                Tokens.replace(outcome.target(), " "));
        log.debug(
                "Batch item reported {} terms, {} verified segmentId={}",
                outcome.terms().size(),
                verified.size(),
                candidate.segment().id());
        current.context().lexicon().record(settings.projectId(), verified);
    }

    private Result<BatchContext> contextOf(
            final Current current,
            final List<Candidate> batch,
            final List<BatchItem> items,
            final List<String> keyTerms) {
        final Candidate first = batch.getFirst();
        final List<Segment> unit = current.work().unitSegments(first.item());
        final int pairs =
                Math.min(MAX_PAIRS, Math.max(MIN_PAIRS, settings.dial().precedingTargets()));
        final List<DraftContext> perItem = perItemContexts(current, batch);
        return preceding
                .earlierPairs(unit, first.segment(), pairs, current.drafts())
                .map(earlier -> new BatchContext(
                        merged(items, perItem),
                        earlier.stream()
                                .map(e -> new BatchContext.Pair(
                                        DisplayText.of(e.segment().masked()), DisplayText.of(e.target())))
                                .toList(),
                        nextSourceAfter(unit, batch.getLast().segment()),
                        characters(perItem),
                        keyTerms));
    }

    private List<DraftContext> perItemContexts(final Current current, final List<Candidate> batch) {
        return batch.stream()
                .map(candidate -> current.context()
                        .contextFor(
                                candidate.segment(),
                                List.of(),
                                candidate.offer().lookup(),
                                summary.get())
                        .draftContext())
                .toList();
    }

    private static DraftContext merged(final List<BatchItem> items, final List<DraftContext> perItem) {
        return BatchContexts.of(items.stream().map(BatchItem::id).toList(), perItem, List.of(), null, List.of())
                .draft();
    }

    // Who is who: each item's own character sheet, so the sheet is the one the budget already cut, joined without
    // repeats.
    private static List<String> characters(final List<DraftContext> perItem) {
        return perItem.stream()
                .flatMap(context -> context.characterLines().stream())
                .distinct()
                .toList();
    }

    private static @Nullable String nextSourceAfter(final List<Segment> unit, final Segment last) {
        for (int i = 0; i < unit.size() - 1; i++) {
            if (unit.get(i).id().equals(last.id())) {
                return DisplayText.of(unit.get(i + 1).masked());
            }
        }
        return null;
    }
}
