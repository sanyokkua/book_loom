package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.QaFinding;

/** The gender check as the quality gate sees it: a soft note that never blocks, chosen by the target language. */
class NarratorGenderQaTest {

    private static final Narrator MALE = new Narrator(NarratorPerson.FIRST, Gender.MALE);

    private static QaResult evaluate(final String target, final String targetLanguage, final Narrator narrator) {
        final SoftCheckInput input = new SoftCheckInput(
                "I closed the door and left the house.",
                target,
                target,
                "en",
                targetLanguage,
                ForeignPassagePolicy.KEEP,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                List.of(),
                narrator);
        return QaEvaluator.evaluate(List.of(), input);
    }

    @Test
    void evaluate_maleNarratorWithFeminineVerb_raisesASoftGenderFindingThatDoesNotBlock() {
        final QaResult qa = evaluate("Я зачинила двері й вийшла з дому.", "uk", MALE);

        assertThat(qa.findings()).extracting(QaFinding::raisedBy).contains("gender");
        assertThat(qa.hardGatesPass()).isTrue();
        assertThat(qa.failedOutright()).isFalse();
        assertThat(qa.findings().stream()
                        .filter(finding -> finding.raisedBy().equals("gender"))
                        .findFirst()
                        .orElseThrow()
                        .note())
                .contains("\"зачинила\"", "narrator is male");
    }

    @Test
    void evaluate_narratorUnspecifiedOrThirdPerson_raisesNothing() {
        assertThat(evaluate("Я зачинила двері й вийшла з дому.", "uk", Narrator.unspecified())
                        .findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("gender");
        assertThat(evaluate("Я зачинила двері й вийшла з дому.", "uk", new Narrator(NarratorPerson.THIRD, Gender.MALE))
                        .findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("gender");
    }

    @Test
    void evaluate_languageWithoutAGenderCheck_raisesNothing() {
        assertThat(evaluate("Ich schloss die Tür und verliess das Haus.", "de", MALE)
                        .findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("gender");
    }
}
