package ua.bookloom.pipeline.batch;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.FocusItem;
import ua.bookloom.pipeline.context.KindExtra;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * Plans the batch draft prompt through the run's context builder (design D8, D11): the run frame's system blocks,
 * byte-identical across a run's calls, and one last user message holding the card, the map lines the window names,
 * the previous pairs, the next source and the numbered items, answered as the JSON items of
 * {@link PromptName#TRANSLATE}.
 */
@Slf4j
public final class BatchPromptBuilder {

    private final TranslatePlanner planner;

    /**
     * Creates the builder of one run.
     *
     * @param planner the non-null planner of the run's translate-family calls
     */
    public BatchPromptBuilder(final TranslatePlanner planner) {
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    /**
     * Plans one batch, its items numbered {@code 1…n} in order.
     *
     * @param context the non-null window, map, memory and summaries the batch is shown
     * @param items the non-null items, at least one, numbered {@code 1…n}
     * @param segmentIds the non-null segments the items stand for, in the items' order
     * @return the fitted plan, saying how many items it kept, or the builder's error
     */
    public Result<ContextBuilder.FittedPlan> plan(
            final TranslateContext context, final List<BatchItem> items, final List<String> segmentIds) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(segmentIds, "segmentIds");
        if (items.isEmpty() || items.size() != segmentIds.size()) {
            throw new IllegalArgumentException(
                    "a batch needs one segment per item: " + items.size() + " items, " + segmentIds.size());
        }
        final Focus focus = new Focus(
                IntStream.range(0, items.size())
                        .mapToObj(i ->
                                new FocusItem(segmentIds.get(i), items.get(i).masked()))
                        .toList(),
                Map.of());
        log.debug(
                "Planning a batch name={} items={} previous={} next={}",
                PromptName.TRANSLATE,
                items.size(),
                context.window().previous().size(),
                context.window().nextSource() != null);
        return planner.plan(CallKind.DRAFT, context, focus, KindExtra.NONE);
    }

    /**
     * Builds the request of a planned batch.
     *
     * @param plan the non-null plan from {@link #plan}
     * @param items the batch items, to size the reply's runaway guard
     * @param lowerTemperature whether a retry asks for the lower temperature
     * @return the request, asking for the batch schema
     */
    public ChatRequest requestFor(final PromptPlan plan, final List<BatchItem> items, final boolean lowerTemperature) {
        final OutputLimit limit = OutputLimit.forBatch(
                items.stream().map(BatchItem::masked).toList(),
                planner.frame().brief().sourceLanguage(),
                Objects.requireNonNull(planner.frame().brief().targetLanguage(), "targetLanguage"));
        final ChatRequest request = ChatRequests.build(PromptName.TRANSLATE, plan, limit, lowerTemperature);
        log.debug("Batch request items={} cap={}", items.size(), request.maxOutputTokens());
        return request;
    }
}
