package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.BookStats.Formatting;

/** The figures the statistics card states beyond what the book's statistics hold verbatim. */
class StructureStatisticsTest {

    private static BookStats withWords(final int words) {
        return new BookStats(0, words, 0, 0, 0, 0, 0, 0, Set.of());
    }

    // IF the estimate were not rounded, THEN "~78,214" would claim a precision the word counter does not have.
    @ParameterizedTest
    @CsvSource({"0,0", "999,999", "1000,1000", "1499,1000", "1500,2000", "78214,78000"})
    void approximateWords_wordCount_roundsToTheNearestThousandFromAThousand(final int words, final int expected) {
        assertThat(new StructureStatistics(withWords(words)).approximateWords()).isEqualTo(expected);
    }

    // IF the kinds came out in set order, THEN the card would list them in a different order from one run to the next.
    @Test
    void protectedFormatting_quotesAndItalics_isListedInTheOrderOfTheKinds() {
        final BookStats stats =
                new BookStats(0, 0, 0, 0, 0, 0, 0, 0, Set.of(Formatting.QUOTES, Formatting.OTHER, Formatting.ITALICS));

        assertThat(new StructureStatistics(stats).protectedFormatting())
                .containsExactly(Formatting.ITALICS, Formatting.QUOTES, Formatting.OTHER);
    }

    @Test
    void protectedFormatting_noKinds_isEmpty() {
        assertThat(new StructureStatistics(withWords(0)).protectedFormatting()).isEmpty();
    }
}
