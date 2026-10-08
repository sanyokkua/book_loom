package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/** How the deterministic text findings enter the gates: blocking ones fail like a hard gate, soft ones only note. */
class TextCheckGatesTest {

    private static final String ENGLISH_PARAGRAPH =
            "The night was cold and the streets were empty, and nobody had seen the old man since the evening.";

    private static SoftCheckInput input(final String source, final String target, final ForeignPassagePolicy policy) {
        return SoftCheckFixtures.scriptEcho(
                source, target, "en", "uk", NamePolicy.TRANSLITERATE, policy, null, List.of());
    }

    private static SoftCheckInput declaredFrench(final String text) {
        return SoftCheckFixtures.scriptEcho(
                text, text, "en", "uk", NamePolicy.TRANSLITERATE, ForeignPassagePolicy.KEEP, "fr", List.of());
    }

    @Test
    void evaluate_englishParagraphLeftInTheTarget_failsAHardGateWithTheLanguageIdentityFinding() {
        final QaResult result = QaEvaluator.evaluate(
                List.of(), input(ENGLISH_PARAGRAPH, ENGLISH_PARAGRAPH, ForeignPassagePolicy.TRANSLATE));

        assertThat(result.hardGatesPass()).isFalse();
        assertThat(result.findings())
                .extracting(QaFinding::raisedBy, QaFinding::kind)
                .contains(tuple("language-identity", "language"));
    }

    // IF reply syntax were typed as a meaning defect, THEN the directed fix would be asked to repair the translation's
    // meaning when only a trailing "} is wrong.
    @Test
    void evaluate_replySyntaxInTheTarget_isAFormatFindingNotAMeaningOne() {
        final QaResult result =
                QaEvaluator.evaluate(List.of(), input("He left.", "Він пішов.}]}", ForeignPassagePolicy.TRANSLATE));

        assertThat(result.hardGatesPass()).isFalse();
        assertThat(result.findings())
                .extracting(QaFinding::raisedBy, QaFinding::kind)
                .contains(tuple("protocol-leak", "markup"))
                .doesNotContain(tuple("protocol-leak", "meaning"));
    }

    @Test
    void evaluate_mixedScriptWord_failsAHardGateAndNamesTheWordInTheNote() {
        final QaResult result = QaEvaluator.evaluate(
                List.of(),
                input(
                        "He studied hard all year.",
                        "Він наполегливо навчg3вся цілий рік.",
                        ForeignPassagePolicy.TRANSLATE));

        assertThat(result.hardGatesPass()).isFalse();
        assertThat(result.findings())
                .filteredOn(finding -> finding.raisedBy().equals("script-purity"))
                .singleElement()
                .satisfies(finding -> assertThat(finding.note()).startsWith("\"навчg3вся\""));
    }

    @Test
    void evaluate_doubledWord_passesTheGatesWithALowFinding() {
        final QaResult result = QaEvaluator.evaluate(
                List.of(),
                input("It is all the same to me, still.", "Мені одно одно таки.", ForeignPassagePolicy.TRANSLATE));

        assertThat(result.hardGatesPass()).isTrue();
        assertThat(result.findings())
                .extracting(QaFinding::raisedBy, QaFinding::severity)
                .contains(tuple("duplicate-word", Severity.LOW));
    }

    @Test
    void evaluate_cleanTranslation_addsNoTextResult() {
        final QaResult result = QaEvaluator.evaluate(
                List.of(),
                input(
                        ENGLISH_PARAGRAPH,
                        "Ніч була холодна, а вулиці порожні, і ніхто не бачив старого відтоді.",
                        ForeignPassagePolicy.TRANSLATE));

        assertThat(result.hardGates()).hasSize(1);
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void evaluate_keptForeignPassage_isNotHeldToTheTargetScript() {
        final String french = "Il faisait nuit et personne ne voulait sortir dans la rue ce soir-là, disait-on.";

        final QaResult result = QaEvaluator.evaluate(List.of(), declaredFrench(french));

        assertThat(result.hardGates()).hasSize(1);
    }

    @Test
    void evaluate_copyrightLineOfLocatorsOnly_isNotFlaggedAsWrongLanguage() {
        final String line = "ISBN 978-3-16-148410-0 www.example.com";

        final QaResult result = QaEvaluator.evaluate(List.of(), input(line, line, ForeignPassagePolicy.TRANSLATE));

        assertThat(result.failedOutright()).isFalse();
        assertThat(result.findings()).isEmpty();
    }
}
