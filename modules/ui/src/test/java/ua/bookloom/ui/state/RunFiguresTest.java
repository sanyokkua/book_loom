package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;

/**
 * The four figures a run shows, derived from the engine's snapshot. The snapshot carries no total and no field called
 * remaining, so each expectation is a hand-written number, never a re-derivation.
 */
class RunFiguresTest {

    private static final double TOLERANCE = 1e-12;

    // IF remaining, total or the proportion were derived differently from the specification, THEN the dashboard would
    // report the wrong count of segments left. The chapter columns must never change any figure.
    @ParameterizedTest(name = "accepted {0} flagged {1} pending {2} section {3}/{4}")
    @CsvSource({
        "768, 3, 469, 1, 1, 469, 1240, 0.6217741935483871",
        "768, 3, 469, 7, 11, 469, 1240, 0.6217741935483871",
        "768, 3, 469, 0, 0, 469, 1240, 0.6217741935483871",
        "0, 0, 5, 1, 1, 5, 5, 0.0",
        "10, 0, 0, 1, 1, 0, 10, 1.0",
        "0, 4, 0, 1, 1, 0, 4, 1.0",
        "1, 1, 2, 1, 1, 2, 4, 0.5"
    })
    void from_snapshot_derivesRemainingTotalAndProportion(
            final int accepted,
            final int flagged,
            final int pending,
            final int section,
            final int sections,
            final int remaining,
            final int total,
            final double fraction) {
        final RunFigures figures =
                RunFigures.from(new JobProgress(JobStage.TRANSLATE, section, sections, accepted, flagged, pending));

        assertThat(figures.accepted()).isEqualTo(accepted);
        assertThat(figures.flagged()).isEqualTo(flagged);
        assertThat(figures.remaining()).isEqualTo(remaining);
        assertThat(figures.total()).isEqualTo(total);
        assertThat(figures.fraction()).isCloseTo(fraction, within(TOLERANCE));
    }

    // IF an empty snapshot divided by its zero total, THEN the dashboard would show NaN instead of no progress.
    @ParameterizedTest
    @CsvSource({"TRANSLATE", "EXPORT"})
    void from_allZeroSnapshot_hasZeroProportionAndNoFailure(final JobStage stage) {
        final RunFigures figures = RunFigures.from(new JobProgress(stage, 0, 0, 0, 0, 0));

        assertThat(figures.fraction()).isEqualTo(0.0);
        assertThat(figures.fraction()).isNotNaN();
        assertThat(figures.total()).isZero();
        assertThat(figures.remaining()).isZero();
    }
}
