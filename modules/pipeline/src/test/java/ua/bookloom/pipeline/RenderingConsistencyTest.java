package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.LexiconEntry;

/** The metric "distinct renderings per recurring term", read by the run's log line, the report and the eval harness. */
class RenderingConsistencyTest {

    private static LexiconEntry used(final String term, final String... renderings) {
        LexiconEntry entry = LexiconEntry.of("p1", term);
        for (final String rendering : renderings) {
            entry = entry.seen(rendering);
        }
        return entry;
    }

    @Test
    void of_everyTermWrittenOneWay_isOne() {
        final RenderingConsistency metric = RenderingConsistency.of(
                List.of(used("master", "господар", "господар", "господар"), used("imp", "біс")));

        assertThat(metric.distinctPerTerm()).isEqualTo(1.0);
        assertThat(metric.conflicted()).isZero();
    }

    @Test
    void of_aDriftingTerm_raisesTheMeanAndCountsTheConflict() {
        final RenderingConsistency metric = RenderingConsistency.of(
                List.of(used("master", "господар", "учитель", "пан"), used("imp", "біс"), LexiconEntry.of("p1", "Mr")));

        assertThat(metric.terms()).isEqualTo(3);
        assertThat(metric.used()).isEqualTo(2);
        assertThat(metric.distinctPerTerm()).isEqualTo(2.0);
        assertThat(metric.conflicted()).isEqualTo(1);
    }

    @Test
    void of_nothingUsed_isZeroAndDescribes() {
        final RenderingConsistency metric = RenderingConsistency.of(List.of(LexiconEntry.of("p1", "master")));

        assertThat(metric.distinctPerTerm()).isZero();
        assertThat(metric.describe())
                .isEqualTo(
                        "lexiconTerms=1 used=0 distinctRenderingsPerTerm=0.00 conflicted=0 learned=0 learnedCoverage=0.00");
    }

    @Test
    void of_learnedTermsWithoutReports_countAsOneRenderingAndReportTheirCoverage() {
        final RenderingConsistency metric = RenderingConsistency.of(List.of(
                LexiconEntry.of("p1", "master").withLearned(new LexiconEntry.Learned("господар", 9, 10)),
                LexiconEntry.of("p1", "imp").withLearned(new LexiconEntry.Learned("біс", 5, 10)),
                LexiconEntry.of("p1", "Mr")));

        assertThat(metric.used()).isEqualTo(2);
        assertThat(metric.distinctPerTerm()).isEqualTo(1.0);
        assertThat(metric.learned()).isEqualTo(2);
        assertThat(metric.learnedCoverage()).isEqualTo(0.7);
        assertThat(metric.describe()).endsWith("learned=2 learnedCoverage=0.70");
    }

    @Test
    void of_learnedTermAlsoReported_keepsTheReportedCount() {
        final RenderingConsistency metric = RenderingConsistency.of(
                List.of(used("master", "господар", "учитель").withLearned(new LexiconEntry.Learned("господар", 3, 4))));

        assertThat(metric.distinctPerTerm()).isEqualTo(2.0);
        assertThat(metric.conflicted()).isEqualTo(1);
    }
}
