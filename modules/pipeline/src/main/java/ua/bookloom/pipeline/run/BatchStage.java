package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchReply;
import ua.bookloom.pipeline.batch.ItemOutcome;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.EchoGate;
import ua.bookloom.pipeline.context.ExampleChoice;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.VerbatimCheck;
import ua.bookloom.pipeline.lexicon.TermMappingVerifier;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.ModelCalls;

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

    private final BatchDrafter drafter;
    private final RunSettings settings;
    private final MemoryReuse memory;
    private final PrecedingTargets preceding;
    private final RoutedCalls calls;
    private final ExampleChoice examples;
    private final ModelCalls notes;

    /**
     * Creates the batch side of one run.
     *
     * @param drafter the non-null drafter, whose size adapts over the run
     * @param settings the non-null run settings
     * @param memory the non-null memory side of the run, whose lookups decide which segments a reuse takes
     * @param preceding the non-null reader of the chapter's earlier targets
     * @param calls the non-null router a batch call goes through
     * @param examples the non-null examples the run's calls show, which an answer must not copy
     * @param notes the non-null seam a batch item's adoption or fallback is noted on, so the shown call says it
     */
    BatchStage(
            final BatchDrafter drafter,
            final RunSettings settings,
            final MemoryReuse memory,
            final PrecedingTargets preceding,
            final RoutedCalls calls,
            final ExampleChoice examples,
            final ModelCalls notes) {
        this.drafter = Objects.requireNonNull(drafter, "drafter");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.preceding = Objects.requireNonNull(preceding, "preceding");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.examples = Objects.requireNonNull(examples, "examples");
        this.notes = Objects.requireNonNull(notes, "notes");
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
        final Result<Optional<PromptRequests.PreparedBatch>> prepared = prepare(current, batch);
        if (prepared.isErr()) {
            return Optional.of(RoutedCalls.failedBy(Objects.requireNonNull(prepared.error(), "error")));
        }
        final Optional<PromptRequests.PreparedBatch> fitted = Objects.requireNonNull(prepared.data(), "prepared");
        if (fitted.isEmpty()) {
            log.warn("No batch fits the window segmentId={}: drafted on its own", firstId);
            current.batches().tried(firstId);
            return Optional.empty();
        }
        return send(current, batch, fitted.get());
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
            if (next != null) {
                if (tokens + cost(next) > budget) {
                    break;
                }
                tokens += cost(next);
                batch.add(next);
            }
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

    // The batch the window allowed is a prefix of the candidates gathered.
    private Optional<RunEnd> send(
            final Current current, final List<Candidate> gathered, final PromptRequests.PreparedBatch prepared) {
        final List<Candidate> batch = gathered.subList(0, prepared.shown().size());
        final List<BatchItem> items = prepared.items();
        final List<String> segmentIds =
                batch.stream().map(candidate -> candidate.segment().id()).toList();
        final List<String> ids = items.stream().map(BatchItem::id).toList();
        log.debug(
                "Drafting a batch of {} segments from segmentId={} ids={}",
                batch.size(),
                segmentIds.getFirst(),
                segmentIds);
        final List<String> sources = batch.stream()
                .map(candidate -> DisplayText.of(candidate.segment().masked()))
                .toList();
        final ChunkPosition position = current.work().position(batch.getFirst().item());
        final Step<BatchAttempt> step = calls.untilAnsweredOrFlagged(
                current.work(),
                null,
                new RoutedCalls.StepName("batch", segmentIds.getFirst()),
                () -> attempt(prepared, new Shown(segmentIds, sources, position)),
                error -> BatchAttempt.unavailable(ids),
                error -> BatchAttempt.skipped(ids, error));
        return switch (step) {
            case Step.Stopped<BatchAttempt>(final RunEnd end) -> Optional.of(end);
            case Step.Done<BatchAttempt>(final BatchAttempt attempt) -> {
                settle(current, batch, prepared.context(), sentSections(attempt, prepared), attempt);
                yield Optional.empty();
            }
        };
    }

    // An answer the draft step would flag its one segment for, such as an empty completion or a context-window
    // error, says nothing about the others: the batch is unavailable and every segment takes its own draft, which flags
    // itself.
    private Result<BatchAttempt> attempt(final PromptRequests.PreparedBatch prepared, final Shown shown) {
        final TranslateContext context = prepared.context();
        final List<BatchItem> items = prepared.items();
        final Result<BatchReply> answered =
                drafter.draft(prepared.plan(), items, shown.segmentIds(), shown.sources(), shown.position());
        final AppError error = answered.error();
        if (error == null) {
            final BatchReply first = Objects.requireNonNull(answered.data(), "reply");
            // A stopped re-ask ends the attempt like a stopped batch call, so no single draft starts after Stop.
            return drafter.reaskMissing(context, items, shown.segmentIds(), shown.sources(), shown.position(), first)
                    .map(merged -> BatchAttempt.answered(merged, first));
        }
        if (PauseDecider.route(error) == PauseDecider.Route.FLAG_AT_ONCE) {
            log.warn(
                    "The batch call answered code={} reason={}; every segment is drafted on its own",
                    error.code(),
                    FailureReason.tokenIn(error.details()));
            return Result.ok(
                    BatchAttempt.unavailable(items.stream().map(BatchItem::id).toList()));
        }
        return Result.err(error);
    }

    // Every segment the batch covered is tried; only the ones the model answered whole and the gate accepted wait for
    // their turn, the rest take their own call there. A skip leaves the others untried: they batch again at their turn.
    private void settle(
            final Current current,
            final List<Candidate> batch,
            final TranslateContext context,
            @Nullable final List<PromptSection> sections,
            final BatchAttempt attempt) {
        if (attempt.kind() == BatchAttempt.Kind.SKIPPED) {
            skipFirst(current, batch.getFirst(), Objects.requireNonNull(attempt.skip(), "skip"));
            return;
        }
        final BatchReply reply = attempt.reply();
        int adopted = 0;
        for (int i = 0; i < batch.size(); i++) {
            final ItemOutcome outcome = reply.outcome(Integer.toString(i + 1)).orElseThrow();
            adopted += settleOne(current, batch.get(i), outcome, new Sent(context, sections)) ? 1 : 0;
        }
        if (attempt.kind() == BatchAttempt.Kind.ANSWERED) {
            drafter.record(attempt.first());
            noteKeyTerms(context, attempt.first());
        }
        logSettled(batch.size(), adopted, attempt.kind());
    }

    // An answer is taken when the model answered the id whole, it copies no shown example and the gate accepts it.
    private boolean settleOne(
            final Current current, final Candidate candidate, final ItemOutcome outcome, final Sent sent) {
        current.batches().tried(candidate.segment().id());
        final boolean copied = outcome.isAccepted() && copiesExample(current, candidate, outcome);
        final boolean isAdopted = outcome.isAccepted() && !copied && adopt(current, candidate, outcome, sent);
        notes.noted(BatchNotes.of(candidate.segment().id(), outcome, isAdopted, copied));
        if (isAdopted) {
            recordTerms(current, candidate, outcome, sent.context().keyTerms());
        } else {
            log.warn(
                    "Batch item falls back to its own draft segmentId={} status={} problems={}",
                    candidate.segment().id(),
                    outcome.status(),
                    outcome.problems());
        }
        return isAdopted;
    }

    // Stored as sent (masked), like the preceding targets; the review desk converts them for display. A re-asked id
    // keeps the first call's sections, since that is the call whose reply the batch adopted from.
    @Nullable
    private static List<PromptSection> sentSections(
            final BatchAttempt attempt, final PromptRequests.PreparedBatch prepared) {
        return attempt.kind() == BatchAttempt.Kind.ANSWERED ? prepared.plan().sections() : null;
    }

    // An answer that copies a shown example is not taken: the segment's own draft is its one re-ask.
    private boolean copiesExample(final Current current, final Candidate candidate, final ItemOutcome outcome) {
        final boolean copied =
                EchoGate.copies(outcome.target(), candidate.mask().maskedText(), examples);
        if (copied) {
            log.warn(
                    "Batch item copied a shown example segmentId={}; asked once more alone",
                    candidate.segment().id());
            current.batches().echoed(candidate.segment().id());
        }
        return copied;
    }

    // An unreadable reply says nothing of whether the model uses the key-term block.
    private void noteKeyTerms(final TranslateContext context, final BatchReply first) {
        if (first.readable()) {
            drafter.keyTermAsks().record(!context.keyTerms().isEmpty(), first.hasTerms());
        }
    }

    /** What the shown batch call names besides its request. */
    private record Shown(List<String> segmentIds, List<String> sources, ChunkPosition position) {}

    private void logSettled(final int segments, final int adopted, final BatchAttempt.Kind kind) {
        log.info(
                "Batch answered segments={} adopted={} fallbacks={} kind={} nextSize={} ceiling={}",
                segments,
                adopted,
                segments - adopted,
                kind,
                drafter.size(),
                drafter.startSize());
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

    /** What the batch an adopted item came from showed it: the context its repair is planned over, and the parts. */
    private record Sent(TranslateContext context, @Nullable List<PromptSection> sections) {}

    private static boolean adopt(
            final Current current, final Candidate candidate, final ItemOutcome outcome, final Sent sent) {
        final Optional<DraftOutcome> drafted =
                current.translator().adopt(candidate.segment(), candidate.mask(), outcome.target());
        drafted.ifPresent(found -> {
            final String id = candidate.segment().id();
            current.batches().ready(id, found);
            current.batches().sent(id, sent.sections());
            current.batches().shown(id, sent.context());
        });
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
                Tokens.replace(outcome.target(), " "),
                List.of(),
                settings.frame().sourceLanguage(),
                settings.frame().targetLanguage());
        log.debug(
                "Batch item reported {} terms, {} verified segmentId={}",
                outcome.terms().size(),
                verified.size(),
                candidate.segment().id());
        current.context().lexicon().record(settings.projectId(), verified);
    }

    // The batch is fitted to the model tier's budgets: what helps least goes first, and only then batch items, the rest
    // of which batch at their own turn. Fewer than two segments is no batch.
    private Result<Optional<PromptRequests.PreparedBatch>> prepare(final Current current, final List<Candidate> batch) {
        final List<PromptRequests.BatchSlot> slots = batch.stream()
                .map(candidate -> new PromptRequests.BatchSlot(
                        candidate.segment(), candidate.mask(), candidate.offer().lookup()))
                .toList();
        return current.requests().batch(slots, preceding.accepted(current.drafts()));
    }
}
