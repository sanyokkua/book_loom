package ua.bookloom.pipeline.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.chunk.BatchSizeController;
import ua.bookloom.pipeline.chunk.BatchSizeController.Failure;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptBreakdown;
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
     * Where a run's batch size starts: the number of items the measured A/B of the small and the large model class both
     * answered with every id once and every token kept.
     */
    public static final int DEFAULT_INITIAL_SIZE = 8;

    /** A batch size of one: no segment is ever drafted with another, so every draft is a single-segment call. */
    public static final int NO_BATCHING = 1;

    /** The smallest size of a run that batches: halving never takes it down to {@link #NO_BATCHING}. */
    public static final int MIN_BATCHING_SIZE = 2;

    /** How many ids a readable reply must leave out before the missing ones are asked again together. */
    public static final int MIN_MISSING_TO_REASK = 2;

    static final int MAX_SIZE = 16;
    static final int CLEAN_STREAK_TO_GROW = 3;

    private final BatchPromptBuilder prompts;
    private final BatchReplyParser parser;
    private final ModelCalls calls;
    private final @Nullable String sourceLanguage;
    private final String targetLanguage;
    private final BatchSizeController size;
    private final KeyTermAsks keyTermAsks = new KeyTermAsks();

    /**
     * Creates the drafter of one run.
     *
     * @param prompts the non-null builder of the run's batch prompts
     * @param parser the non-null reader of batch replies
     * @param calls the non-null seam every call of the run goes through
     * @param sourceLanguage the source language tag, or null when the book declares none
     * @param targetLanguage the non-null target language tag
     * @param initialSize the first batch size, from {@link #NO_BATCHING} to 16
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
        this.size = new BatchSizeController(min, MAX_SIZE, initialSize, CLEAN_STREAK_TO_GROW);
    }

    /** The run's memory of whether its model answers the key-term block, which decides if the next prompt asks. */
    public KeyTermAsks keyTermAsks() {
        return keyTermAsks;
    }

    /** The items the next batch may carry, before the token budget cuts it. */
    public int size() {
        return size.size();
    }

    /**
     * What a batch's prompt would weigh, so a run can fit it to the window before it sends it.
     *
     * @param context the non-null context shown with the items
     * @param items the non-null items, at least one
     * @return the estimated tokens of the whole prompt
     */
    public int promptTokens(final BatchContext context, final List<BatchItem> items) {
        return PromptBreakdown.of(prompts.messagesFor(context, items)).total();
    }

    /**
     * Sends one batch and reads its reply.
     *
     * @param context the non-null read-only context shown with the items
     * @param items the non-null items, at least one, with unique ids
     * @param segmentIds the non-null segments the items stand for, in the items' order, which the call announces
     * @return the reply read per id — not readable when the model answered nothing usable — or the error the call
     *     answered, which the run routes
     */
    public Result<BatchReply> draft(
            final BatchContext context, final List<BatchItem> items, final List<String> segmentIds) {
        return draft(
                context,
                items,
                segmentIds,
                items.stream().map(item -> DisplayText.of(item.masked())).toList(),
                null);
    }

    /**
     * Sends one batch made in a known chunk and reads its reply, as {@link #draft(BatchContext, List, List)} does.
     *
     * @param context the non-null read-only context shown with the items
     * @param items the non-null items, at least one, with unique ids
     * @param segmentIds the non-null segments the items stand for, in the items' order
     * @param sources the non-null source of each segment as a person reads it, in the items' order
     * @param position the chunk the batch is made in, which the shown call names, or null when it is not known
     * @return as {@link #draft(BatchContext, List, List)}
     */
    public Result<BatchReply> draft(
            final BatchContext context,
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
        final ChatRequest request = request(context, items);
        final CallDescriptor descriptor = new CallDescriptor(
                PromptName.DRAFT_BATCH_JSON.resourceBaseName(),
                position,
                sources,
                () -> prompts.sectionsFor(context, items));
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
        return Result.ok(reply);
    }

    /**
     * Asks once more for the ids a readable reply left out, when it left out {@value #MIN_MISSING_TO_REASK} or more:
     * only those items go, with the same context, so the model has fewer to number and the rest of the reply is kept.
     * One id left out is not worth a call of its own, since its single-segment draft costs the same. A failed or
     * unreadable second call changes nothing, so those ids fall back one by one as before.
     *
     * @param context the non-null context the first call showed
     * @param items the non-null items of the first call
     * @param segmentIds the non-null segments of the first call, in the items' order
     * @param sources the non-null source of each segment as a person reads it, in the items' order
     * @param position the chunk the batch is made in, or null when it is not known
     * @param first the non-null reply of the first call
     * @return {@code first} with each re-asked id taken from the second reply, or {@code first} itself when no call
     *     was made or it brought nothing
     */
    public BatchReply reaskMissing(
            final BatchContext context,
            final List<BatchItem> items,
            final List<String> segmentIds,
            final List<String> sources,
            @Nullable final ChunkPosition position,
            final BatchReply first) {
        Objects.requireNonNull(first, "first");
        final long missing = first.count(ItemStatus.MISSING);
        if (!first.readable() || missing < MIN_MISSING_TO_REASK) {
            log.debug("No re-ask readable={} missing={}", first.readable(), missing);
            return first;
        }
        final List<Integer> at = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (first.outcome(items.get(i).id())
                    .filter(o -> o.status() == ItemStatus.MISSING)
                    .isPresent()) {
                at.add(i);
            }
        }
        log.info("Batch reply left out {} of {} ids; asking for those only", at.size(), items.size());
        final Result<BatchReply> second = draft(
                context,
                at.stream().map(items::get).toList(),
                at.stream().map(segmentIds::get).toList(),
                at.stream().map(sources::get).toList(),
                position);
        final BatchReply again = second.data();
        if (again == null || !again.readable()) {
            log.warn("The re-ask for the missing ids brought nothing; they fall back one by one");
            return first;
        }
        return withAnswers(first, again);
    }

    private static BatchReply withAnswers(final BatchReply first, final BatchReply again) {
        final List<ItemOutcome> merged = first.outcomes().stream()
                .map(outcome -> outcome.status() == ItemStatus.MISSING
                        ? again.outcome(outcome.id())
                                .filter(second -> second.status() != ItemStatus.EXTRA)
                                .orElse(outcome)
                        : outcome)
                .toList();
        log.debug("Re-ask answered ids={}", again.acceptedIds());
        return new BatchReply(true, merged);
    }

    /**
     * The request one batch's call sends, before the run's seam sizes it to the window: the same one {@link #draft}
     * sends, so a caller that only needs the request (a prompt eval) builds exactly the run's.
     *
     * @param context the non-null read-only context shown with the items
     * @param items the non-null items, at least one
     * @return the request asking for the batch schema, with the reply capped by the items' size
     */
    public ChatRequest request(final BatchContext context, final List<BatchItem> items) {
        return prompts.requestFor(prompts.messagesFor(context, items), items, false);
    }

    /**
     * Feeds a batch's first reply to the adaptive size, never a re-ask's, so a model that needs the second call still
     * shrinks the batches: a lost numbering, an omission or a merge halves it, a reply whose
     * ids were all right counts toward growing it. An item that only failed its own checks is the item's problem, not
     * the size's.
     *
     * @param reply the non-null reply of the batch just made
     */
    public void record(final BatchReply reply) {
        Objects.requireNonNull(reply, "reply");
        final Optional<Failure> failure = failureOf(reply);
        failure.ifPresentOrElse(size::recordFailure, size::recordClean);
        log.debug("Batch recorded failure={} nextSize={}", failure.orElse(null), size.size());
    }

    static Optional<Failure> failureOf(final BatchReply reply) {
        if (reply.count(ItemStatus.MERGED_SUSPECT) > 0) {
            return Optional.of(Failure.MERGE);
        }
        if (reply.count(ItemStatus.EXTRA) > 0) {
            return Optional.of(Failure.WRONG_ID);
        }
        final boolean omitted =
                !reply.readable() || reply.count(ItemStatus.MISSING) > 0 || reply.count(ItemStatus.DUPLICATE) > 0;
        return omitted ? Optional.of(Failure.OMISSION) : Optional.empty();
    }
}
