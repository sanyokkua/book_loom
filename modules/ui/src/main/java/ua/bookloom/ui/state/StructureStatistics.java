package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;

/**
 * The figures of {@link BookStats} in the shape the structure screen's statistics card reads them: the word count
 * rounded, the footnotes and tables, and the protected formatting kinds in a fixed order.
 *
 * @param stats the opened book's statistics
 */
public record StructureStatistics(BookStats stats) {

    private static final int THOUSAND = 1_000;

    /** Rejects missing statistics. */
    public StructureStatistics {
        Objects.requireNonNull(stats, "stats");
    }

    /**
     * The word count as the card states it, which is an estimate and is worded as one: to the nearest thousand once
     * the book has a thousand words, because a count of tens says more than the tokenizer knows.
     *
     * @return the rounded word count; the exact count below a thousand
     */
    public int approximateWords() {
        final int words = stats.words();
        if (words < THOUSAND) {
            return words;
        }
        return Math.round((float) words / THOUSAND) * THOUSAND;
    }

    /**
     * The inline formatting kinds the book carries.
     *
     * @return the kinds in the order of {@link Formatting}, never null; empty when the book has none
     */
    public List<Formatting> protectedFormatting() {
        return Stream.of(Formatting.values())
                .filter(stats.formatting()::contains)
                .toList();
    }
}
