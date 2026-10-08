package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaResult;

/** Edited text that evaluates worse than the text before the edits is thrown away. */
class EditRegressionTest {

    private static QaResult qa(final double confidence, final List<CheckResult> soft) {
        return new QaResult(
                List.of(), soft, confidence, List.of(), soft.stream().anyMatch(CheckResult::blocking));
    }

    private static final CheckName CHECK = CheckName.SCRIPT;

    @Test
    void isWorse_moreBlockingFindings_true() {
        final QaResult before = qa(0.9, List.of());
        final QaResult after = qa(0.9, List.of(CheckResult.fail(CHECK, "x")));

        assertThat(EditRegression.isWorse(before, after)).isTrue();
    }

    @Test
    void isWorse_lowerScore_true() {
        assertThat(EditRegression.isWorse(qa(0.9, List.of()), qa(0.8, List.of())))
                .isTrue();
    }

    @Test
    void isWorse_sameBlockersAndScoreNotLower_false() {
        assertThat(EditRegression.isWorse(qa(0.8, List.of()), qa(0.8, List.of())))
                .isFalse();
        assertThat(EditRegression.isWorse(qa(0.8, List.of()), qa(0.9, List.of())))
                .isFalse();
    }
}
