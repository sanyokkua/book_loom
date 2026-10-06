package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.typography.QuoteRepair;

/**
 * The quote defects of the real-run corpus (15e.5) through the deterministic quote repair with no model: each shape the
 * 6-hour run flagged is repaired to the case's good rendering, and the placeholder-in-a-word case is left alone.
 */
class RealRunQuoteRepairTest {

    private static RunCase caseOf(final String id) {
        return RunCorpus.text().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "quotes-stray-close-1",
                "quotes-stray-close-2",
                "quotes-unclosed-1",
                "quotes-unclosed-2",
                "quotes-crossed-1",
                "quotes-crossed-nested",
                "quotes-doubled-close"
            })
    void repair_realRunQuoteDefect_becomesTheGoodRendering(final String id) {
        final RunCase runCase = caseOf(id);

        final QuoteRepair.Repaired repaired = QuoteRepair.repair(
                runCase.source(), runCase.bad(), RunCorpus.SOURCE_LANGUAGE, RunCorpus.TARGET_LANGUAGE);

        assertThat(repaired.text()).isEqualTo(runCase.good());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quotes-placeholder-mid-word", "quotes-nested-ascii"})
    void repair_placeholderInAWordOrNothingBlocking_isLeftAlone(final String id) {
        final RunCase runCase = caseOf(id);

        final QuoteRepair.Repaired repaired = QuoteRepair.repair(
                runCase.source(), runCase.bad(), RunCorpus.SOURCE_LANGUAGE, RunCorpus.TARGET_LANGUAGE);

        assertThat(repaired.isChanged()).isFalse();
    }
}
