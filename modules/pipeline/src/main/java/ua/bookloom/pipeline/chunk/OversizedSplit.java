package ua.bookloom.pipeline.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SentenceSplitter;

/**
 * Plans how a segment above the chunk budget is drafted: in sentence-aligned pieces the joined translation of which
 * goes back into the one node the segment came from, so the structure stays intact.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OversizedSplit {

    /** The split language when the book's source language is unknown: the root rules of the sentence iterator. */
    private static final String UNDETERMINED = "und";

    /** How an oversized segment is drafted. */
    public sealed interface Plan permits Pieces, Unsplittable {}

    /**
     * Consecutive sentences merged greedily while each piece stays within the budget.
     *
     * @param pieces the pieces in order; they concatenate to the text the segment is shown as
     */
    public record Pieces(List<String> pieces) implements Plan {

        /** Copies the pieces so the plan cannot change after it is made. */
        public Pieces {
            pieces = List.copyOf(pieces);
        }
    }

    /** The splitter found no boundary to cut at, so the segment is drafted whole. */
    public record Unsplittable() implements Plan {}

    /**
     * Plans the pieces of one segment.
     *
     * @param segment the segment whose text is above the budget; never null
     * @param shownText the text the model is shown for the segment — its masked text, protected spans behind tokens
     *     of their own — which the pieces are cut from; never null
     * @param sourceLanguage the source language tag, or null when unknown
     * @param budgetTokens the most tokens one piece may hold, unless one sentence alone exceeds it
     * @param splitter the sentence splitter; never null
     * @return the pieces, or {@link Unsplittable} when the splitter yields one piece or fails
     */
    public static Plan plan(
            final Segment segment,
            final String shownText,
            @Nullable final String sourceLanguage,
            final int budgetTokens,
            final SentenceSplitter splitter) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(shownText, "shownText");
        Objects.requireNonNull(splitter, "splitter");
        final int estimate = TokenEstimator.estimate(shownText, sourceLanguage);
        log.debug(
                "Planning oversized split segment={} estimate={} budget={} language={}",
                segment.id(),
                estimate,
                budgetTokens,
                sourceLanguage);
        final Result<List<String>> split =
                splitter.split(shownText, segment, sourceLanguage == null ? UNDETERMINED : sourceLanguage);
        if (split.isErr() || Objects.requireNonNull(split.data()).size() < 2) {
            log.debug("Oversized segment={} has no sentence boundary to cut at", segment.id());
            return new Unsplittable();
        }
        final List<String> pieces = merge(split.data(), sourceLanguage, budgetTokens);
        log.debug(
                "Oversized split decision segment={} estimate={} budget={} pieces={} sizes={}",
                segment.id(),
                estimate,
                budgetTokens,
                pieces.size(),
                pieces.stream().map(String::length).toList());
        return pieces.size() < 2 ? new Unsplittable() : new Pieces(pieces);
    }

    private static List<String> merge(
            final List<String> sentences, @Nullable final String sourceLanguage, final int budgetTokens) {
        final List<String> pieces = new ArrayList<>();
        StringBuilder open = new StringBuilder();
        for (final String sentence : sentences) {
            if (!open.isEmpty() && TokenEstimator.estimate(open + sentence, sourceLanguage) > budgetTokens) {
                pieces.add(open.toString());
                open = new StringBuilder();
            }
            open.append(sentence);
        }
        pieces.add(open.toString());
        return pieces;
    }
}
