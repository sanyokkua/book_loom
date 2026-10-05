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
    private final BatchFit fit;

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
        this.fit = new BatchFit(drafter, settings);
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
        final Result<Optional<Prepared>> prepared = prepare(current, batch);
        if (prepared.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(prepared.error(), "error")));
        }
        final Optional<Prepared> fitted = Objects.requireNonNull(prepared.data(), "prepared");
        if (fitted.isEmpty()) {
            log.warn("No batch fits the window segmentId={}: drafted on its own", firstId);
            current.batches().tried(firstId);
            return Optional.empty();
        }
        return send(current, fitted.get());
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

    private Optional<RunEnd> send(final Current current, final Prepared prepared) {
        final List<Candidate> batch = prepared.batch();
        final List<BatchItem> items = prepared.items();
        final List<String> segmentIds =
                batch.stream().map(candidate -> candidate.segment().id()).toList();
        final List<String> ids = items.stream().map(BatchItem::id).toList();
        log.debug(
                "Drafting a batch of {} segments from segmentId={} ids={}",
                batch.size(),
                segmentIds.getFirst(),
                segmentIds);
        final Step<BatchAttempt> step = calls.untilAnsweredOrFlagged(
                current.work(),
                null,
                new RoutedCalls.StepName("batch", segmentIds.getFirst()),
                () -> attempt(prepared.context(), items, segmentIds),
                error -> BatchAttempt.unavailable(ids),
                error -> BatchAttempt.skipped(ids, error));
        return switch (step) {
            case Step.Stopped<BatchAttempt>(final RunEnd end) -> Optional.of(end);
            case Step.Done<BatchAttempt>(final BatchAttempt attempt) -> {
                settle(current, prepared, attempt);
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
    // nothing about the others: the batch is unavailable and every segment takes its own draft, which flags itself.
    private Result<BatchAttempt> attempt(
            final BatchContext context, final List<BatchItem> items, final List<String> segmentIds) {
        final Result<BatchReply> answered = drafter.draft(context, items, segmentIds);
        final AppError error = answered.error();
        if (error == null) {
            return Result.ok(BatchAttempt.answered(Objects.requireNonNull(answered.data(), "reply")));
        }
        if (PauseDecider.route(error.code()) == PauseDecider.Route.FLAG_AT_ONCE) {
            log.warn("The batch call answered code={}; every segment is drafted on its own", error.code());
            return Result.ok(
                    BatchAttempt.unavailable(items.stream().map(BatchItem::id).toList()));
        }
        return Result.err(error);
    }

    // Every segment the batch covered is tried; only the ones the model answered whole and the gate accepted wait for
    // their turn, the rest take their own call there. A skip leaves the others untried: they batch again at their turn.
    private void settle(final Current current, final Prepared prepared, final BatchAttempt attempt) {
        final List<Candidate> batch = prepared.batch();
        if (attempt.kind() == BatchAttempt.Kind.SKIPPED) {
            skipFirst(current, batch.getFirst(), Objects.requireNonNull(attempt.skip(), "skip"));
            return;
        }
        final BatchReply reply = attempt.reply();
        int adopted = 0;
        for (int i = 0; i < batch.size(); i++) {
            final Candidate candidate = batch.get(i);
            current.batches().tried(candidate.segment().id());
            final ItemOutcome outcome = reply.outcome(Integer.toString(i + 1)).orElseThrow();
            if (outcome.isAccepted() && adopt(current, candidate, outcome)) {
                adopted++;
                recordTerms(current, candidate, outcome, prepared.context().keyTerms());
            } else {
                log.warn(
                        "Batch item falls back to its own draft segmentId={} status={} problems={}",
                        candidate.segment().id(),
                        outcome.status(),
                        outcome.problems());
            }
        }
        if (attempt.kind() == BatchAttempt.Kind.ANSWERED) {
            drafter.record(reply);
        }
        logSettled(batch.size(), adopted, attempt.kind());
    }

    private void logSettled(final int segments, final int adopted, final BatchAttempt.Kind kind) {
        log.info(
                "Batch answered segments={} adopted={} fallbacks={} kind={} nextSize={}",
                segments,
                adopted,
                segments - adopted,
                kind,
                drafter.size());
    }

    // The step is named by its first segment, so a skip skips that one, flagged as a single draft's skip is.
    private static void skipFirst(final Current current, final Candidate first, final AppError error) {
        log.info(
                "Batch skipped as asked; its first segment is flagged segmentId={}",
                first.segment().id());
        current.batches().tried(first.segment().id());
        current.batches()
                .ready(
                        first.segment().id(),
                        new DraftOutcome.FlaggedAtOnce(
                                first.segment(), first.segment().masked(), List.of(), error));
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

    /** What one batch call is made of: the segments it covers, their items and the context it carries. */
    private record Prepared(List<Candidate> batch, List<BatchItem> items, BatchContext context) {}

    // The batch is shown to the model whole when its prompt fits the window; else without the optional context, else
    // with fewer segments, the rest of which batch at their own turn. Fewer than two segments is no batch.
    private Result<Optional<Prepared>> prepare(final Current current, final List<Candidate> batch) {
        final Candidate first = batch.getFirst();
        final List<Segment> unit = current.work().unitSegments(first.item());
        final int pairs =
                Math.min(MAX_PAIRS, Math.max(MIN_PAIRS, settings.dial().precedingTargets()));
        return preceding
                .earlierPairs(unit, first.segment(), pairs, current.drafts())
                .map(earlier -> shrinkToFit(current, batch, unit, earlier));
    }

    private Optional<Prepared> shrinkToFit(
            final Current current,
            final List<Candidate> batch,
            final List<Segment> unit,
            final List<PrecedingTargets.Earlier> earlier) {
        final List<BatchContext.Pair> pairs = fit.capPairs(earlier.stream()
                .map(e -> new BatchContext.Pair(DisplayText.of(e.segment().masked()), DisplayText.of(e.target())))
                .toList());
        List<Candidate> shown = batch;
        while (shown.size() >= BatchDrafter.MIN_BATCHING_SIZE) {
            final Prepared full = prepared(current, shown, unit, pairs);
            if (fit.fits(full.context(), full.items())) {
                return Optional.of(full);
            }
            final Prepared lean = new Prepared(shown, full.items(), BatchFit.lean(full.context()));
            if (fit.fits(lean.context(), lean.items())) {
                log.debug("Batch of {} fits only without its optional context", shown.size());
                return Optional.of(lean);
            }
            shown = shown.subList(0, shown.size() > BatchDrafter.MIN_BATCHING_SIZE ? (shown.size() + 1) / 2 : 0);
        }
        return Optional.empty();
    }

    private Prepared prepared(
            final Current current,
            final List<Candidate> shown,
            final List<Segment> unit,
            final List<BatchContext.Pair> pairs) {
        final List<BatchItem> items = itemsOf(shown);
        final List<DraftContext> perItem = perItemContexts(current, shown);
        final List<String> keyTerms = current.context()
                .keyTermsIn(shown.stream().map(Candidate::segment).toList());
        final BatchContext context = new BatchContext(
                merged(items, perItem),
                pairs,
                fit.capNext(BatchFit.nextSourceAfter(unit, shown.getLast().segment())),
                BatchFit.characters(perItem),
                keyTerms);
        return new Prepared(shown, items, context);
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
}
