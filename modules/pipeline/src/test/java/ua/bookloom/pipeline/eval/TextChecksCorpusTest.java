package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.checks.TextChecks;

/**
 * The deterministic text checks against the regression corpus, with no model: recall on the defect families code can
 * decide must be total and no faithful candidate may be flagged — the number the later reviewer work has to keep.
 */
class TextChecksCorpusTest {

    private static final Set<String> DECIDABLE_KINDS =
            Set.of("mixed-script", "quotes", "leftover-language", "duplicate", "spacing");

    private static List<DefectRow> rows() {
        return EvalCorpus.defects().stream()
                .filter(corpusCase -> DECIDABLE_KINDS.contains(corpusCase.kind()))
                .map(corpusCase -> new DefectRow(
                        corpusCase.id(),
                        corpusCase.kind(),
                        corpusCase.defective(),
                        !TextChecks.run(corpusCase.source(), corpusCase.candidate(), "en", "uk")
                                .isEmpty(),
                        true,
                        true,
                        1))
                .toList();
    }

    @Test
    void textChecks_decidableCorpusFamilies_haveFullRecall() {
        assertThat(rows()).hasSizeGreaterThanOrEqualTo(15);
        assertThat(EvalMetrics.falseNegativeRate(rows())).isZero();
    }

    @Test
    void textChecks_decidableCorpusFamilies_flagNoFaithfulCandidate() {
        assertThat(EvalMetrics.falsePositiveRate(rows())).isZero();
    }
}
