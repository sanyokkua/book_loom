package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.checks.DictionaryWordValidator;
import ua.bookloom.pipeline.checks.WordValidator;

/** The word validator as the quality gate sees it: a low note that never blocks, and no change when it is the no-op. */
class WordValidatorQaTest {

    private static final String TARGET = "Він сидів на кафедрахрі й мовчки дивився у вікно.";
    private static final Set<String> KNOWN =
            Set.of("він", "сидів", "на", "й", "мовчки", "дивився", "у", "вікно", "кафедрі");

    private static SoftCheckInput input() {
        return new SoftCheckInput(
                "He sat on the department and looked out of the window in silence.",
                TARGET,
                TARGET,
                "en",
                "uk",
                ForeignPassagePolicy.KEEP,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                List.of());
    }

    @Test
    void evaluate_noOpValidator_isExactlyTheResultWithoutOne() {
        assertThat(QaEvaluator.evaluate(List.of(), input(), WordValidator.none()))
                .isEqualTo(QaEvaluator.evaluate(List.of(), input()));
    }

    @Test
    void evaluate_dictionaryDoubtsAWord_raisesOneLowNoteThatDoesNotBlock() {
        final QaResult qa = QaEvaluator.evaluate(List.of(), input(), new DictionaryWordValidator(KNOWN::contains));

        assertThat(qa.hardGatesPass()).isTrue();
        assertThat(qa.failedOutright()).isFalse();
        assertThat(qa.findings())
                .filteredOn(finding -> finding.raisedBy().equals("unknown-word"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity()).isEqualTo(Severity.LOW);
                    assertThat(finding.kind()).isEqualTo("fluency");
                    assertThat(finding.note()).contains("\"кафедрахрі\"");
                });
        assertThat(qa.findings()).extracting(QaFinding::raisedBy).containsOnlyOnce("unknown-word");
    }

    @Test
    void evaluate_validatorThatKnowsEveryWord_raisesNothing() {
        assertThat(QaEvaluator.evaluate(List.of(), input(), new DictionaryWordValidator(word -> true))
                        .findings())
                .isEmpty();
    }
}
