package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.heal.AcceptanceRule;

/** How an address mix enters the gates: a repair-level finding that passes the hard gates and earns a directed fix. */
class AddressMixGateTest {

    private static final String SOURCE = "“You are tired, Nell. Sit down, you need rest, and I will bring you tea.”";

    private static QaResult evaluate(final String target) {
        return QaEvaluator.evaluate(
                List.of(),
                SoftCheckFixtures.scriptEcho(
                        SOURCE,
                        target,
                        "en",
                        "uk",
                        NamePolicy.TRANSLITERATE,
                        ForeignPassagePolicy.TRANSLATE,
                        null,
                        List.of()));
    }

    @Test
    void evaluate_mixedAddress_passesTheHardGatesWithAMediumAddressMixFinding() {
        final QaResult result = evaluate("«Ти втомилася, Нелл. Сядьте, Вам треба відпочити, а я принесу Вам чаю.»");

        assertThat(result.hardGatesPass()).isTrue();
        assertThat(result.findings())
                .extracting(QaFinding::raisedBy, QaFinding::severity)
                .contains(tuple("address-mix", Severity.MEDIUM));
    }

    // IF the finding were not at the repair level, THEN the mix would only be noted and the book would keep it.
    @Test
    void repairFindings_mixedAddress_isTheOneFindingTheRepairMends() {
        final QaResult result = evaluate("«Ти втомилася, Нелл. Сядьте, Вам треба відпочити, а я принесу Вам чаю.»");

        assertThat(AcceptanceRule.decide(result).repair())
                .extracting(QaFinding::raisedBy)
                .containsExactly("address-mix");
    }

    @Test
    void evaluate_addressedOneWay_hasNoAddressMixFinding() {
        final QaResult result = evaluate("«Ти втомилася, Нелл. Сядь, тобі треба відпочити, а я принесу тобі чаю.»");

        assertThat(result.findings()).extracting(QaFinding::raisedBy).doesNotContain("address-mix");
        assertThat(AcceptanceRule.decide(result).repair()).isEmpty();
    }

    @Test
    void level_addressMixFinding_isRepairAndADuplicateWordIsNot() {
        assertThat(CheckLevels.of(new QaFinding("language", Severity.MEDIUM, "x", "address-mix")))
                .isEqualTo(CheckLevel.REPAIR);
        assertThat(CheckLevels.of(new QaFinding("fluency", Severity.LOW, "x", "duplicate-word")))
                .isNotEqualTo(CheckLevel.REPAIR);
    }
}
