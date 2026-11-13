package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.checks.TextChecks;

/**
 * The deterministic text checks against the regression corpus, with no model: recall on the defect families code can
 * decide must be total and no faithful candidate may be flagged.
 */
class TextChecksCorpusTest {

    private static final Set<String> DECIDABLE_KINDS =
            Set.of("mixed-script", "quotes", "leftover-language", "duplicate", "spacing");

    private static List<DefectCase> decidable() {
        return EvalCorpus.defects().stream()
                .filter(corpusCase -> DECIDABLE_KINDS.contains(corpusCase.kind()))
                .toList();
    }

    private static boolean flags(final DefectCase corpusCase) {
        return !TextChecks.run(corpusCase.source(), corpusCase.candidate(), "en", "uk", List.of(), List.of())
                .isEmpty();
    }

    @Test
    void textChecks_decidableCorpusFamilies_haveFullRecall() {
        assertThat(decidable()).hasSizeGreaterThanOrEqualTo(15);
        assertThat(decidable().stream().filter(DefectCase::defective)).allMatch(TextChecksCorpusTest::flags);
    }

    @Test
    void textChecks_decidableCorpusFamilies_flagNoFaithfulCandidate() {
        assertThat(decidable().stream().filter(corpusCase -> !corpusCase.defective()))
                .noneMatch(TextChecksCorpusTest::flags);
    }
}
