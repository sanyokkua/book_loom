package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Severity;

/** {@link QaEvaluator}: the refusal gate, the five soft checks and the given hard gates, combined into one result. */
class QaEvaluatorTest {

    private static final String EN = "en";
    private static final String UK = "uk";
    private static final String PLACEHOLDER_NOTE = "placeholder multiset mismatch";

    @Test
    void evaluate_upperCasedEcho_confidenceMatchesReference() {
        final SoftCheckInput input = SoftCheckFixtures.scriptEcho(
                "He opened the old door.",
                "HE OPENED THE OLD DOOR.",
                EN,
                UK,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.TRANSLATE,
                null,
                List.of());

        final QaResult result = QaEvaluator.evaluate(List.of(), input);

        assertThat(result.confidence()).isCloseTo(0.65, within(1e-9));
        assertThat(result.failedOutright()).isTrue();
        assertThat(result.hardGatesPass()).isTrue();
        final CheckResult script = result.soft().get(0);
        final CheckResult echo = result.soft().get(1);
        assertThat(script.blocking()).isTrue();
        assertThat(script.finding()).isNotNull();
        assertThat(script.finding().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(script.finding().kind()).isEqualTo("language");
        assertThat(script.finding().raisedBy()).isEqualTo("script");
        assertThat(echo.blocking()).isTrue();
        assertThat(echo.finding()).isNotNull();
        assertThat(echo.finding().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(echo.finding().kind()).isEqualTo("language");
        assertThat(echo.finding().raisedBy()).isEqualTo("echo");
        assertThat(result.findings()).hasSize(2);
    }

    @Test
    void evaluate_echoBelowFloor_notBlockingAtHigherConfidence() {
        final SoftCheckInput input = SoftCheckFixtures.scriptEcho(
                "Yes, sir.",
                "YES, SIR.",
                EN,
                UK,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.TRANSLATE,
                null,
                List.of());

        final QaResult result = QaEvaluator.evaluate(List.of(), input);

        assertThat(result.confidence()).isCloseTo(0.85, within(1e-9));
        assertThat(result.failedOutright()).isFalse();
        final CheckResult script = result.soft().get(0);
        final CheckResult echo = result.soft().get(1);
        assertThat(script.skipped()).isTrue();
        assertThat(echo.blocking()).isFalse();
        assertThat(echo.finding()).isNotNull();
        assertThat(echo.finding().severity()).isEqualTo(Severity.LOW);
        assertThat(echo.finding().raisedBy()).isEqualTo("echo");
        assertThat(result.findings()).hasSize(1);
    }

    @Test
    void evaluate_emptyTargetWithNonEmptySource_reportsFailedRefusalGate() {
        final SoftCheckInput input = SoftCheckFixtures.scriptEcho(
                "He opened the old door.",
                "",
                EN,
                UK,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.TRANSLATE,
                null,
                List.of());

        final QaResult result = QaEvaluator.evaluate(List.of(), input);

        assertThat(result.hardGatesPass()).isFalse();
        assertThat(result.hardGates())
                .filteredOn(gate -> gate.check() == CheckName.REFUSAL)
                .singleElement()
                .satisfies(refusal -> {
                    assertThat(refusal.blocking()).isTrue();
                    assertThat(refusal.finding()).isNotNull();
                    assertThat(refusal.finding().severity()).isEqualTo(Severity.HIGH);
                    assertThat(refusal.finding().kind()).isEqualTo("meaning");
                    assertThat(refusal.finding().raisedBy()).isEqualTo("refusal");
                });
        assertThat(result.confidence()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void evaluate_refusalPhraseTarget_reportsFailedRefusalGate() {
        final SoftCheckInput input = SoftCheckFixtures.scriptEcho(
                "He opened the old door.",
                "I'm sorry, but I can't translate this text.",
                EN,
                UK,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.TRANSLATE,
                null,
                List.of());

        final QaResult result = QaEvaluator.evaluate(List.of(), input);

        assertThat(result.hardGatesPass()).isFalse();
        assertThat(result.hardGates())
                .filteredOn(gate -> gate.check() == CheckName.REFUSAL)
                .singleElement()
                .satisfies(refusal -> {
                    assertThat(refusal.blocking()).isTrue();
                    assertThat(refusal.finding()).isNotNull();
                    assertThat(refusal.finding().severity()).isEqualTo(Severity.HIGH);
                    assertThat(refusal.finding().kind()).isEqualTo("meaning");
                    assertThat(refusal.finding().raisedBy()).isEqualTo("refusal");
                });
    }

    @Test
    void evaluate_failedPlaceholderGateGiven_appearsAmongHardGates() {
        final SoftCheckInput input = SoftCheckFixtures.scriptEcho(
                "Yes, sir.",
                "YES, SIR.",
                EN,
                UK,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.TRANSLATE,
                null,
                List.of());
        final CheckResult failedPlaceholder = CheckResult.hardGateFailed(CheckName.PLACEHOLDER, PLACEHOLDER_NOTE);

        final QaResult result = QaEvaluator.evaluate(List.of(failedPlaceholder), input);

        assertThat(result.hardGatesPass()).isFalse();
        assertThat(result.hardGates()).contains(failedPlaceholder);
        assertThat(result.findings()).contains(failedPlaceholder.finding());
        assertThat(result.confidence()).isCloseTo(0.85, within(1e-9));
    }
}
