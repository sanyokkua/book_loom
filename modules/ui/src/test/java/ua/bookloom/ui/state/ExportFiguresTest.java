package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ReviewCounts;

/**
 * The figures an export screen derives from the project's counts: what is translated, what could be, and whether a
 * book written now would be partial or empty.
 */
class ExportFiguresTest {

    private static ExportFigures figures(
            final int total, final int sourceKept, final int pending, final int flaggedWithoutTarget) {
        return new ExportFigures(
                new ReviewCounts(total, 0, 0, flaggedWithoutTarget, 0, pending, sourceKept, flaggedWithoutTarget, 0),
                null);
    }

    // IF a segment kept as it was counted as work to do, THEN a finished book would read as partial.
    @ParameterizedTest
    @CsvSource({
        "142, 0, 68, 0, 74, 142, true, false",
        "142, 10, 0, 0, 132, 132, false, false",
        "10, 0, 10, 0, 0, 10, true, true",
        "10, 2, 3, 1, 4, 8, true, false"
    })
    void figures_countsOfABook_deriveTranslatedOfTotalPartialAndNothing(
            final int total,
            final int sourceKept,
            final int pending,
            final int flaggedWithoutTarget,
            final int translated,
            final int ofTotal,
            final boolean partial,
            final boolean nothing) {
        final ExportFigures figures = figures(total, sourceKept, pending, flaggedWithoutTarget);

        assertThat(figures.translated()).isEqualTo(translated);
        assertThat(figures.ofTotal()).isEqualTo(ofTotal);
        assertThat(figures.isPartial()).isEqualTo(partial);
        assertThat(figures.isNothing()).isEqualTo(nothing);
    }
}
