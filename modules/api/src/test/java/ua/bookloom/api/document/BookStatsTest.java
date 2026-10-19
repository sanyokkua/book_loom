package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code BookStats}'s non-negative-count and defensive-copy invariants.
 */
class BookStatsTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void constructor_negativeCountAtFieldIndex_isRejected(final int fieldIndex) {
        final int[] counts = {0, 0, 0, 0, 0, 0, 0, 0};
        counts[fieldIndex] = -1;

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BookStats(
                        counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6], counts[7],
                        Set.of()));
    }

    @Test
    void constructor_mutatingSourceSetAfterConstruction_doesNotChangeStoredFormatting() {
        final Set<BookStats.Formatting> formatting = new HashSet<>(Set.of(BookStats.Formatting.ITALICS));
        final BookStats stats = new BookStats(1, 2, 0, 0, 0, 0, 0, 0, formatting);

        formatting.add(BookStats.Formatting.BOLD);

        assertThat(stats.formatting()).containsExactly(BookStats.Formatting.ITALICS);
    }
}
