package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The quick subsets (15j.J16): each id exists, each set stays small, the switch reads the environment. */
class QuickEvalTest {

    private static final int AT_MOST = 10;

    @Test
    void promptCases_quickSet_holdsOnlyExistingCasesAndAtMostTen() {
        final List<String> all =
                PromptEvalCases.ALL.stream().map(EvalCase::name).toList();

        assertThat(QuickEval.PROMPT_CASES).isNotEmpty().hasSizeLessThanOrEqualTo(AT_MOST);
        assertThat(all).containsAll(QuickEval.PROMPT_CASES);
        assertThat(QuickEval.PROMPT_CASES).doesNotHaveDuplicates();
    }

    @Test
    void promptCases_quickSet_coversDraftFixReviewAndAControl() {
        final List<EvalCase> quick = PromptEvalCases.ALL.stream()
                .filter(evalCase -> QuickEval.PROMPT_CASES.contains(evalCase.name()))
                .toList();

        assertThat(quick).hasAtLeastOneElementOfType(EvalCase.Draft.class);
        assertThat(quick).hasAtLeastOneElementOfType(EvalCase.Fix.class);
        assertThat(quick).hasAtLeastOneElementOfType(EvalCase.Review.class);
        assertThat(QuickEval.PROMPT_CASES).contains("plain-short");
    }

    @Test
    void realRun_quickMarks_areAtMostTenAcrossTheFiles() {
        assertThat(QuickEval.realRunQuickIds()).isNotEmpty().hasSizeLessThanOrEqualTo(AT_MOST);
    }

    @Test
    void realRun_quickMarks_coverMoreThanOneCaseFile() {
        assertThat(RunCorpus.text().stream().filter(RunCase::isQuick)).isNotEmpty();
        assertThat(RunCorpus.batches().stream().filter(BatchCase::isQuick)).isNotEmpty();
        assertThat(RunCorpus.reviewerBatches().stream().filter(ReviewerBatchCase::isQuick))
                .isNotEmpty();
        assertThat(RunCorpus.repairs().stream().filter(RepairCase::isQuick)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true"})
    void isOn_switchSet_isTrue(final String value) {
        assertThat(QuickEval.isOn(Map.of("BOOKLOOM_EVAL_QUICK", value))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "no"})
    void isOn_switchOffOrBlank_isFalse(final String value) {
        assertThat(QuickEval.isOn(Map.of("BOOKLOOM_EVAL_QUICK", value))).isFalse();
    }

    @Test
    void isOn_switchAbsent_isFalse() {
        assertThat(QuickEval.isOn(Map.of())).isFalse();
    }
}
