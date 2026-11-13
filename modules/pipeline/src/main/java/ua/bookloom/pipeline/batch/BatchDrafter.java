package ua.bookloom.pipeline.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.chunk.BatchSizeController;
import ua.bookloom.pipeline.chunk.BatchSizeController.Failure;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * Drafts several consecutive segments in one model call and reads the reply per id. It makes the call and judges the
 * reply's shape; what a run does with an accepted id (the placeholder gate, the quality loop) and how a failing id
 * falls back to a single-segment draft is the caller's, so an id the model got wrong costs one more call and never
 * more. It also owns the adaptive batch size, which halves when the model loses the numbering and grows back slowly.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class BatchDrafter {

    /**
     * A batch size for a drafter built without a model profile (tests and evals); a run starts at its model profile's
     * size (redesign task 10.8).
     */
    public static final int DEFAULT_INITIAL_SIZE = 8;

    /** A batch size of one: no segment is ever drafted with another, so every draft is a single-segment call. */
    public static final int NO_BATCHING = 1;

    /** The smallest size of a run that batches: halving never takes it down to {@link #NO_BATCHING}. */
    public static final int MIN_BATCHING_SIZE = 2;

    /** How many ids a readable reply must leave out before the missing ones are asked again together. */
    public static final int MIN_MISSING_TO_REASK = 2;

    static final int CLEAN_STREAK_TO_GROW = 3;

    /** How many first-item-only or cut replies in a row halve the size, because one of them says nothing but a habit does. */
    static final int STALLS_TO_HALVE = 3;

    private final BatchPromptBuilder prompts;
    private final BatchReplyParser parser;
    private final ModelCalls calls;
    private final @Nullable String sourceLanguage;
    private final String targetLanguage;
    private final BatchSizeController size;
    private final int startSize;
    private int stallStreak;

    /**
     * Creates the drafter of one run.
     *
     * @param prompts the non-null builder of the run's batch prompts
     * @param parser the non-null reader of batch replies
     * @param calls the non-null seam every call of the run goes through
     * @param sourceLanguage the source language tag, or null when the book declares none
     * @param targetLanguage the non-null target language tag
     * @param initialSize the first batch size, at least {@link #NO_BATCHING}; it is also the ceiling the size never grows above
     */
    public BatchDrafter(
            final BatchPromptBuilder prompts,
            final BatchReplyParser parser,
            final ModelCalls calls,
            @Nullable final String sourceLanguage,
            final String targetLanguage,
            final int initialSize) {
        this.prompts = Objects.requireNonNull(prompts, "prompts");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        // A run that batches never halves its way into no batching, which only an initial size of one asks for.
        final int min = initialSize < MIN_BATCHING_SIZE ? NO_BATCHING : MIN_BATCHING_SIZE;
        this.size = new BatchSizeController(min, initialSize, initialSize, CLEAN_STREAK_TO_GROW);
        this.startSize = initialSize;
    }

    /** The size the run started at, which is also the most a batch may carry: halving may lower the size, growth stops here. */
    public int startSize() {
        return startSize;
    }

    /** The items the next batch may carry, before the token budget cuts it. */
    public int size() {
        return size.size();
    }

    /**
     * Sends one batch and reads its reply.
     *
     * @param context the non-null window, map and memory shown with the items
     * @param items the non-null items, at least one, numbered {@code 1…n}
     * @param segmentIds the non-null segments the items stand for, in the items' order, which the call announces
     * @return the reply read per id (not readable when nothing usable came), or the error the call answered
     */
    public Result<BatchReply> draft(
            final TranslateContext context, final List<BatchItem> items, final List<String> segmentIds) {
        return draft(
                context,
                items,
                segmentIds,
                items.stream().map(item -> DisplayText.of(item.masked())).toList(),
                null);
    }

    /**
     * The plan of a batch of these items, as {@link #draft} sends it.
     *
     * @param context the non-null window, map and memory shown with the items
     * @param items the non-null items, numbered {@code 1…n}
     * @param segmentIds the non-null segments the items stand for
     * @return the fitted plan, or the builder's error
     */
    public Result<ContextBuilder.FittedPlan> plan(
            final TranslateContext context, final List<BatchItem> items, final List<String> segmentIds) {
        return prompts.plan(context, items, segmentIds);
    }

    /**
     * Sends one batch made in a known chunk and reads its reply, as {@link #draft(TranslateContext, List, List)} does.
     *
     * @param context the non-null window, map and memory shown with the items
     * @param items the non-null items, at least one, numbered {@code 1…n}
     * @param segmentIds the non-null segments the items stand for, in the items' order
     * @param sources the non-null source of each segment as a person reads it, in the items' order
     * @param position the chunk the batch is made in, which the shown call names, or null when it is not known
     * @return as {@link #draft(TranslateContext, List, List)}
     */
    public Result<BatchReply> draft(
            final TranslateContext context,
            final List<BatchItem> items,
            final List<String> segmentIds,
            final List<String> sources,
            @Nullable final ChunkPosition position) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(items, "items");
        log.debug(
                "Drafting a batch items={} segmentIds={} size={} position={}",
                items.size(),
                segmentIds,
                size.size(),
                position);
        final Result<ContextBuilder.FittedPlan> planned = prompts.plan(context, items, segmentIds);
        final ContextBuilder.FittedPlan fitted = planned.data();
        if (fitted == null) {
            return Result.err(Objects.requireNonNull(planned.error(), "error"));
        }
        if (fitted.keptItems() < items.size()) {
            log.warn(
                    "The batch's plan keeps {} of its {} items; the rest are left to their own drafts",
                    fitted.keptItems(),
                    items.size());
        }
        return draft(fitted.plan(), items, segmentIds, sources, position);
    }

    /**
     * Sends one batch planned already, as {@link #draft(TranslateContext, List, List, List, ChunkPosition)} does.
     *
     * @param plan the non-null plan of these items
     * @param items the non-null items, at least one, numbered {@code 1…n}
     * @param segmentIds the non-null segments the items stand for, in the items' order
     * @param sources the non-null source of each segment as a person reads it, in the items' order
     * @param position the chunk the batch is made in, or null when it is not known
     * @return as {@link #draft(TranslateContext, List, List)}
     */
    public Result<BatchReply> draft(
            final PromptPlan plan,
            final List<BatchItem> items,
            final List<String> segmentIds,
            final List<String> sources,
            @Nullable final ChunkPosition position) {
        Objects.requireNonNull(plan, "plan");
        final ChatRequest request = prompts.requestFor(plan, items, false);
        final CallDescriptor descriptor =
                new CallDescriptor(PromptName.TRANSLATE.resourceBaseName(), position, sources, plan::sections);
        final Result<ChatResponse> answered = calls.callAbout(CallKind.DRAFT, segmentIds, request, descriptor);
        final ChatResponse response = answered.data();
        if (response == null) {
            return Result.err(Objects.requireNonNull(answered.error(), "error"));
        }
        if (log.isTraceEnabled()) {
            log.trace("Batch reply finish={} content={}", response.finishReason(), response.content());
        }
        final List<String> ids = items.stream().map(BatchItem::id).toList();
        final BatchReply reply = response.content().isBlank()
                ? BatchReply.unreadable(ids)
                : parser.parse(response.content(), items, sourceLanguage, targetLanguage);
        calls.parsed(segmentIds, BatchCallItems.of(ids, segmentIds, reply));
        return Result.ok(reply.withCut(response.finishReason() == FinishReason.LENGTH));
    }

    /**
     * Asks once more for the ids a readable reply left out, when it left out {@value #MIN_MISSING_TO_REASK} or more:
     * only those items go, with the same context, so the model has fewer to number and the rest of the reply is kept.
     * One id left out is not worth a call of its own, since its single-segment draft costs the same. A failed or
     * unreadable second call changes nothing, so those ids fall back one by one as before. Each call shows its own
     * items: the first call keeps the missing ones marked, the second shows only the ids it was asked.
     *
     * @param context the non-null context the first call showed
     * @param items the non-null items of the first call
     * @param segmentIds the non-null segments of the first call, in the items' order
     * @param sources the non-null source of each segment as a person reads it, in the items' order
     * @param position the chunk the batch is made in, or null when it is not known
     * @param first the non-null reply of the first call
     * @return {@code first} with each re-asked id taken from the second reply, or {@code first} itself when no call
     *     was made or it brought nothing; {@code cancelled} when the run was stopped or the step skipped during the
     *     second call, so no single draft follows
     */
    public Result<BatchReply> reaskMissing(
            final TranslateContext context,
            final List<BatchItem> items,
            final List<String> segmentIds,
            final List<String> sources,
            @Nullable final ChunkPosition position,
            final BatchReply first) {
        Objects.requireNonNull(first, "first");
        final long missing = first.count(ItemStatus.MISSING);
        if (!first.readable() || missing < MIN_MISSING_TO_REASK) {
            log.debug("No re-ask readable={} missing={}", first.readable(), missing);
            return Result.ok(first);
        }
        final List<Integer> at = missingAt(items, first);
        log.info("Batch reply left out {} of {} ids; asking for those only", at.size(), items.size());
        final String key = "reask:" + segmentIds.get(at.getFirst());
        calls.steps().added(StepKind.RE_ASK, "re-ask", key, segmentIds.get(at.getFirst()));
        // The re-ask numbers its own items from 1, as every batch does, and its reply is read back under the first ids.
        final List<BatchItem> renumbered = IntStream.range(0, at.size())
                .mapToObj(i -> new BatchItem(
                        Integer.toString(i + 1), items.get(at.get(i)).masked()))
                .toList();
        final Map<String, String> back = new HashMap<>();
        IntStream.range(0, at.size())
                .forEach(i ->
                        back.put(Integer.toString(i + 1), items.get(at.get(i)).id()));
        final Result<BatchReply> second = draft(
                        context,
                        renumbered,
                        at.stream().map(segmentIds::get).toList(),
                        at.stream().map(sources::get).toList(),
                        position)
                .map(reply -> reply.renamed(back));
        return ReAskMerge.merge(first, second, calls.steps(), key);
    }

    private static List<Integer> missingAt(final List<BatchItem> items, final BatchReply first) {
        return IntStream.range(0, items.size())
                .filter(i -> first.outcome(items.get(i).id())
                        .filter(o -> o.status() == ItemStatus.MISSING)
                        .isPresent())
                .boxed()
                .toList();
    }

    /**
     * The request one planned batch sends, before the run's seam sizes it to the window: the same one {@link #draft}
     * sends, so a caller that only needs the request (a prompt eval) builds exactly the run's.
     *
     * @param plan the non-null plan of the batch
     * @param items the non-null items, at least one
     * @return the request, asking for the batch schema, with the reply capped by the plan and the items' size
     */
    public ChatRequest request(final PromptPlan plan, final List<BatchItem> items) {
        return prompts.requestFor(plan, items, false);
    }

    /**
     * Feeds a batch's first reply to the adaptive size, never a re-ask's, so a model that needs the second call still
     * shrinks the batches: a lost numbering, an omission or a merge halves it, a reply whose ids were all right counts
     * toward growing it. Neither a reply that answered only the first item nor one cut at the length cap says
     * anything about the size (a 2-item batch stops early the same way, and a cut was a loop), so one of them leaves
     * it as it is; their missing ids are asked again as for any omission. {@value #STALLS_TO_HALVE} of them in a row
     * are a habit of the model, though, and halve the size once; any other reply starts the count again. An item that
     * only failed its own checks is the item's problem, not the size's.
     *
     * @param reply the non-null reply of the batch just made
     */
    public void record(final BatchReply reply) {
        Objects.requireNonNull(reply, "reply");
        final Outcome outcome = outcomeOf(reply);
        final int before = size.size();
        outcome.failure().ifPresent(size::recordFailure);
        if (outcome == Outcome.CLEAN) {
            size.recordClean();
        }
        countStall(outcome);
        log.debug(
                "Batch recorded outcome={} sent={} size={} nextSize={} ceiling={}",
                outcome,
                reply.outcomes().size() - reply.count(ItemStatus.EXTRA),
                before,
                size.size(),
                startSize);
    }

    // A stall alone says nothing, but a model that always stops after item one must still be able to shrink its batch.
    private void countStall(final Outcome outcome) {
        if (!outcome.isStall()) {
            stallStreak = 0;
            return;
        }
        stallStreak++;
        if (stallStreak >= STALLS_TO_HALVE) {
            log.debug("Batch answered short {} times in a row: halving", stallStreak);
            size.recordFailure(Failure.OMISSION);
            stallStreak = 0;
        }
    }

    /** What a first reply says about the batch size; only the kinds with a {@link Failure} shrink it. */
    enum Outcome {
        /** Every id was right. */
        CLEAN(null, false),
        /** Only the first item came back and the model stopped normally: no size signal alone. */
        FIRST_ONLY(null, true),
        /** The reply hit the length cap with ids missing: no size signal alone. */
        CUT(null, true),
        /** Some ids were left out, or the reply was unreadable. */
        OMISSION(Failure.OMISSION, false),
        /** The reply named an id the batch never held. */
        WRONG_ID(Failure.WRONG_ID, false),
        /** The reply carries two items under one id. */
        MERGE(Failure.MERGE, false);

        private final @Nullable Failure failure;
        private final boolean stall;

        Outcome(final @Nullable Failure failure, final boolean stall) {
            this.failure = failure;
            this.stall = stall;
        }

        boolean isStall() {
            return stall;
        }

        Optional<Failure> failure() {
            return Optional.ofNullable(failure);
        }
    }

    static Outcome outcomeOf(final BatchReply reply) {
        if (reply.count(ItemStatus.MERGED_SUSPECT) > 0) {
            return Outcome.MERGE;
        }
        if (reply.count(ItemStatus.EXTRA) > 0) {
            return Outcome.WRONG_ID;
        }
        final boolean omitted =
                !reply.readable() || reply.count(ItemStatus.MISSING) > 0 || reply.count(ItemStatus.DUPLICATE) > 0;
        if (!omitted) {
            return Outcome.CLEAN;
        }
        if (reply.readable() && reply.cut()) {
            return Outcome.CUT;
        }
        return isFirstOnly(reply) ? Outcome.FIRST_ONLY : Outcome.OMISSION;
    }

    // The first expected id answered, every other one absent: the model chose to stop after one entry.
    private static boolean isFirstOnly(final BatchReply reply) {
        final List<ItemOutcome> expected = reply.outcomes();
        return expected.size() > 1
                && expected.getFirst().status() != ItemStatus.MISSING
                && expected.stream().skip(1).allMatch(outcome -> outcome.status() == ItemStatus.MISSING);
    }
}
