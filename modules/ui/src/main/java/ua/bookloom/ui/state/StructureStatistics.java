package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;

/**
 * The figures of {@link BookStats} in the shape the structure screen's statistics card reads them: the protected
 * formatting kinds in a fixed order.
 *
 * @param stats the opened book's statistics
 */
public record StructureStatistics(BookStats stats) {

    /** Rejects missing statistics. */
    public StructureStatistics {
        Objects.requireNonNull(stats, "stats");
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
