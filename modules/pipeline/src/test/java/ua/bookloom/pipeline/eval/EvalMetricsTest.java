package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalMetricsTest {

    private static DefectRow row(final boolean defective, final boolean flagged, final boolean stable, final int runs) {
        return new DefectRow("c", "k", defective, flagged, true, stable, runs, 0);
    }

    private static final List<DefectRow> ROWS = List.of(
            row(true, true, true, 5),
            row(true, false, false, 5),
            row(false, false, true, 5),
            row(false, true, true, 5),
            row(false, false, true, 5),
            row(false, false, true, 5));

    @Test
    void falseNegativeRate_oneOfTwoDefectsLeftAlone_isHalf() {
        assertThat(EvalMetrics.falseNegativeRate(ROWS)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void falsePositiveRate_oneOfFourCleanChanged_isQuarter() {
        assertThat(EvalMetrics.falsePositiveRate(ROWS)).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void catchRate_oneOfTwoDefectsFlagged_isHalf() {
        assertThat(EvalMetrics.catchRate(ROWS)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void tokenBreaks_rowsThatBrokeTokens_areSummed() {
        assertThat(EvalMetrics.tokenBreaks(List.of(
                        new DefectRow("a", "k", true, true, true, true, 1, 2),
                        new DefectRow("b", "k", false, false, true, true, 1, 1))))
                .isEqualTo(3);
    }

    @Test
    void stability_fiveOfSixStable_isFiveSixths() {
        assertThat(EvalMetrics.stability(ROWS)).isCloseTo(5.0 / 6.0, within(1e-9));
    }

    @Test
    void stability_singleRuns_isPerfect() {
        assertThat(EvalMetrics.stability(List.of(row(true, true, true, 1)))).isEqualTo(1.0);
    }

    @Test
    void defects_corpusFile_loadsBothLabels() {
        final List<DefectCase> corpus = EvalCorpus.defects();

        assertThat(corpus).hasSizeGreaterThanOrEqualTo(15);
        assertThat(corpus).extracting(DefectCase::defective).contains(true, false);
        assertThat(corpus).extracting(DefectCase::id).doesNotHaveDuplicates();
    }
}
