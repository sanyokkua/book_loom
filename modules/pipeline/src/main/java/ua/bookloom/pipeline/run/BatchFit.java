package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.ContextBudget;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * Keeps a batch prompt inside the run's window. The chunk budget sizes the items against the window less the dynamic
 * allowance, but a batch prompt adds the previous pairs and the next source on top of the items' joined contexts, none
 * of which the per-item cut bounds as a whole, so the assembled prompt is measured here before it is sent.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class BatchFit {

    // The previous pairs may take this share of the dynamic allowance, the next source a smaller one.
    private static final int PAIRS_SHARE_DIVISOR = 2;
    private static final int NEXT_SHARE_DIVISOR = 4;

    private final BatchDrafter drafter;
    private final RunSettings settings;
    private final int allowance;

    /**
     * Creates the fit of one run.
     *
     * @param drafter the non-null drafter that renders and weighs a batch prompt
     * @param settings the non-null run settings, whose window and languages decide the fit
     */
    BatchFit(final BatchDrafter drafter, final RunSettings settings) {
        this.drafter = Objects.requireNonNull(drafter, "drafter");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.allowance = ChunkBudget.dynamicAllowance(settings.frame(), settings.window());
    }

    /**
     * Whether the prompt, the reply its items are expected to need and the safety margin fit the window.
     *
     * @param context the non-null context shown with the items
     * @param items the non-null items
     * @return {@code true} if they fit, {@code false} if the batch must shrink
     */
    boolean fits(final BatchContext context, final List<BatchItem> items) {
        final int source = items.stream()
                .mapToInt(item ->
                        TokenEstimator.estimate(item.masked(), settings.frame().sourceLanguage()))
                .sum();
        final double ratio = TokenEstimator.outputRatio(
                settings.frame().sourceLanguage(), settings.frame().targetLanguage());
        final int needed =
                drafter.promptTokens(context, items) + (int) Math.ceil(source * ratio) + ContextBudget.SAFETY_MARGIN;
        log.debug("Batch prompt measured items={} needed={} window={}", items.size(), needed, settings.window());
        return needed <= settings.window();
    }

    /** The newest of the previous pairs that fit their share of the allowance, oldest first. */
    List<BatchContext.Pair> capPairs(final List<BatchContext.Pair> pairs) {
        final int room = allowance / PAIRS_SHARE_DIVISOR;
        final List<BatchContext.Pair> kept = new ArrayList<>();
        int tokens = 0;
        for (int i = pairs.size() - 1; i >= 0; i--) {
            tokens += TokenEstimator.estimate(
                            pairs.get(i).source(), settings.frame().sourceLanguage())
                    + TokenEstimator.estimate(
                            pairs.get(i).target(), settings.frame().targetLanguage());
            if (tokens > room) {
                break;
            }
            kept.addFirst(pairs.get(i));
        }
        log.debug("Capped the previous pairs offered={} kept={} room={}", pairs.size(), kept.size(), room);
        return kept;
    }

    /** The next source when it fits its share of the allowance, else null. */
    @Nullable
    String capNext(@Nullable final String nextSource) {
        if (nextSource == null) {
            return null;
        }
        final int tokens = TokenEstimator.estimate(nextSource, settings.frame().sourceLanguage());
        final boolean fits = tokens <= allowance / NEXT_SHARE_DIVISOR;
        log.debug("Next source tokens={} kept={}", tokens, fits);
        return fits ? nextSource : null;
    }

    /** The context with only what a locked name and the unit's story need: glossary lines and the summary. */
    static BatchContext lean(final BatchContext context) {
        final DraftContext draft = context.draft();
        return new BatchContext(
                new DraftContext(List.of(), draft.summary(), draft.glossaryLines(), List.of()),
                List.of(),
                null,
                List.of());
    }

    // Who is who: each item's own character sheet, so the sheet is the one the budget already cut, joined without
    // repeats.
    static List<String> characters(final List<DraftContext> perItem) {
        return perItem.stream()
                .flatMap(context -> context.characterLines().stream())
                .distinct()
                .toList();
    }

    static @Nullable String nextSourceAfter(final List<Segment> unit, final Segment last) {
        for (int i = 0; i < unit.size() - 1; i++) {
            if (unit.get(i).id().equals(last.id())) {
                return DisplayText.of(unit.get(i + 1).masked());
            }
        }
        return null;
    }
}
