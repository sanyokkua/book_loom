package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;

/**
 * The figures a run shows, derived from the engine's snapshot. The snapshot carries no total and no field called
 * remaining, so each expectation is a hand-written number, never a re-derivation.
 */
class RunFiguresTest {

    private static final double TOLERANCE = 1e-12;

    // IF the total, the proportion or the indices were derived differently from the specification, THEN the
    // dashboard would report the wrong count of segments left or move the bar when only the position changed.
    @Test
    void from_700AutoAccepted68Repaired3Flagged469PendingInSection7Of11_givesTheStatedFigures() {
        final RunFigures figures =
                RunFigures.from(new JobProgress(JobStage.TRANSLATE, 7, 11, 768, 3, 469, 41, 66, 700, 68));

        assertThat(figures.autoAccepted()).isEqualTo(700);
        assertThat(figures.repaired()).isEqualTo(68);
        assertThat(figures.accepted()).isEqualTo(768);
        assertThat(figures.flagged()).isEqualTo(3);
        assertThat(figures.remaining()).isEqualTo(469);
        assertThat(figures.total()).isEqualTo(1240);
        assertThat(figures.fraction()).isCloseTo(771.0 / 1240.0, within(TOLERANCE));
        assertThat(figures.section()).isEqualTo(7);
        assertThat(figures.sections()).isEqualTo(11);
        assertThat(figures.chunk()).isEqualTo(41);
        assertThat(figures.chunks()).isEqualTo(66);
    }

    // IF the indices fed a count, THEN moving to a later chapter or chunk would move the bar.
    @ParameterizedTest(name = "section {0}/{1} chunk {2}/{3}")
    @CsvSource({"1, 1, 1, 1", "7, 11, 41, 66", "0, 0, 0, 0"})
    void from_sameCountsAtAnotherPosition_giveTheSameTotalAndFraction(
            final int section, final int sections, final int chunk, final int chunks) {
        final RunFigures figures = RunFigures.from(
                new JobProgress(JobStage.TRANSLATE, section, sections, 768, 3, 469, chunk, chunks, 700, 68));

        assertThat(figures.total()).isEqualTo(1240);
        assertThat(figures.fraction()).isCloseTo(0.6217741935483871, within(TOLERANCE));
    }

    // IF an empty snapshot divided by its zero total, THEN the dashboard would show NaN instead of no progress.
    @ParameterizedTest
    @CsvSource({"PREP", "TRANSLATE", "REVISE"})
    void from_allZeroSnapshot_hasZeroProportionAndNoFailure(final JobStage stage) {
        final RunFigures figures = RunFigures.from(new JobProgress(stage, 0, 0, 0, 0, 0));

        assertThat(figures.fraction()).isEqualTo(0.0);
        assertThat(figures.fraction()).isNotNaN();
        assertThat(figures.total()).isZero();
        assertThat(figures.remaining()).isZero();
    }

    @Test
    void from_everySegmentDecided_hasFractionOne() {
        final RunFigures figures = RunFigures.from(new JobProgress(JobStage.TRANSLATE, 1, 1, 9, 1, 0, 1, 1, 6, 3));

        assertThat(figures.total()).isEqualTo(10);
        assertThat(figures.fraction()).isEqualTo(1.0);
    }

    // IF a chapter number kept as it is were left out of the decided count, THEN the bar would never reach the end.
    @Test
    void from_twoKeptAsIs_areDecidedButNeitherAutoAcceptedNorRepaired() {
        final RunFigures figures = RunFigures.from(new JobProgress(JobStage.TRANSLATE, 1, 1, 9, 1, 0, 1, 1, 5, 2, 2));

        assertThat(figures.autoAccepted()).isEqualTo(5);
        assertThat(figures.repaired()).isEqualTo(2);
        assertThat(figures.keptVerbatim()).isEqualTo(2);
        assertThat(figures.accepted()).isEqualTo(9);
        assertThat(figures.total()).isEqualTo(10);
        assertThat(figures.fraction()).isEqualTo(1.0);
    }
}
