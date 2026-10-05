package ua.bookloom.pipeline.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * Joins the per-segment contexts of a batch into the one the batch prompt carries. Glossary, memory and suggestion
 * lines are the union of the items' lines, repeats dropped. A locked-name line names a token, and tokens are numbered
 * per segment, so it is prefixed with its item's id ({@code 3: ⟦g0⟧ → name}); the same name on two items stays two
 * lines, each naming its own item's token.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BatchContexts {

    private static final Pattern LOCKED_LINE = Pattern.compile("^⟦g");

    /**
     * Builds the batch context.
     *
     * @param itemIds the non-null ids of the batch's items, in order
     * @param perItem the non-null context of each item, in the same order; its preceding targets are not used
     * @param precedingPairs the non-null chapter's last decided pairs before the batch, oldest first
     * @param nextSource the plain source of the segment after the batch, or null at the end of the unit
     * @param characters the non-null who-is-who lines for the characters present
     * @return the context; the summary is the first non-blank one among the items'
     */
    public static BatchContext of(
            final List<String> itemIds,
            final List<DraftContext> perItem,
            final List<BatchContext.Pair> precedingPairs,
            @Nullable final String nextSource,
            final List<String> characters) {
        Objects.requireNonNull(itemIds, "itemIds");
        Objects.requireNonNull(perItem, "perItem");
        if (itemIds.size() != perItem.size()) {
            throw new IllegalArgumentException("one context per item: " + itemIds.size() + " ids, " + perItem.size());
        }
        final List<String> glossary = new ArrayList<>();
        final List<String> memory = new ArrayList<>();
        final List<String> suggested = new ArrayList<>();
        final List<String> lexicon = new ArrayList<>();
        String summary = null;
        for (int i = 0; i < perItem.size(); i++) {
            final DraftContext context = perItem.get(i);
            summary = summary == null ? context.summary() : summary;
            for (final String line : context.glossaryLines()) {
                glossary.add(LOCKED_LINE.matcher(line).find() ? itemIds.get(i) + ": " + line : line);
            }
            memory.addAll(context.memoryLines());
            suggested.addAll(context.suggestedLines());
            lexicon.addAll(context.lexiconLines());
        }
        logJoined(itemIds.size(), glossary, memory, suggested, precedingPairs, nextSource);
        log.debug("Joined lexicon lines={}", lexicon.size());
        return new BatchContext(
                new DraftContext(List.of(), summary, glossary, memory, suggested, lexicon),
                precedingPairs,
                nextSource,
                characters);
    }

    private static void logJoined(
            final int items,
            final List<String> glossary,
            final List<String> memory,
            final List<String> suggested,
            final List<BatchContext.Pair> pairs,
            @Nullable final String nextSource) {
        log.debug(
                "Joined batch context items={} glossaryLines={} memoryLines={} suggestedLines={} pairs={} next={}",
                items,
                glossary.size(),
                memory.size(),
                suggested.size(),
                pairs.size(),
                nextSource != null);
    }
}
