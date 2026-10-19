package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaEvaluator;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.qa.SoftCheckInput;

/**
 * {@link AcceptanceRule#accepts}: the hard gates pass, no deterministic check blocks and no verified blocker is left;
 * confidence only orders segments and never decides ({@code specs/quality-gates/spec.md} "Accept a segment only by
 * the acceptance rule").
 */
class AcceptanceRuleTest {

    @ParameterizedTest
    @CsvSource({"0.0", "0.30", "0.70", "0.95", "1.0"})
    void accepts_cleanChecksAtAnyConfidence_accepted(final double confidence) {
        assertThat(AcceptanceRule.accepts(qa(confidence, false, false), 0)).isTrue();
    }

    @Test
    void accepts_aFailedHardGate_blocksRegardlessOfConfidence() {
        assertThat(AcceptanceRule.accepts(qa(1.0, true, false), 0)).isFalse();
    }

    @Test
    void accepts_aSoftCheckFailedOutright_blocksRegardlessOfConfidence() {
        assertThat(AcceptanceRule.accepts(qa(1.0, false, true), 0)).isFalse();
    }

    @Test
    void accepts_realFailedLengthCheck_blocks() {
        final CheckResult failedLength = CheckResult.fail(CheckName.LENGTH, "length ratio 0.47 outside [0.7,1.8]");
        final QaResult qa = new QaResult(List.of(), List.of(failedLength), 0.95, List.of(failedLength.finding()), true);

        assertThat(AcceptanceRule.accepts(qa, 0)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "5"})
    void accepts_aVerifiedBlockerLeft_blocksEvenWhenEveryCheckPasses(final int blockersLeft) {
        assertThat(AcceptanceRule.accepts(qa(0.95, false, false), blockersLeft)).isFalse();
    }

    @Test
    void accepts_lowConfidenceFromSoftMarginsThatAllPassed_isNoLongerAReason() {
        final List<CheckResult> soft = List.of(
                CheckResult.pass(CheckName.SCRIPT, 0.5),
                CheckResult.skip(CheckName.ECHO),
                CheckResult.skip(CheckName.REPETITION),
                CheckResult.pass(CheckName.LENGTH, 0.2),
                CheckResult.skip(CheckName.GLOSSARY));

        assertThat(AcceptanceRule.accepts(new QaResult(List.of(), soft, 0.70, List.of(), false), 0))
                .isTrue();
    }

    @Test
    void readyForReview_cleanChecks_isTrue() {
        assertThat(AcceptanceRule.readyForReview(qa(0.9, false, false))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true"})
    void readyForReview_textACheckRefuses_isFalse(final boolean hardGateFailed, final boolean failedOutright) {
        assertThat(AcceptanceRule.readyForReview(qa(0.9, hardGateFailed, failedOutright)))
                .isFalse();
    }

    @Test
    void accepts_contextMatchedReuseAtFullConfidence_accepted() {
        final QaResult qa = QaEvaluator.evaluate(List.of(), reuse("Yes.", "Так."));

        assertThat(qa.confidence()).isEqualTo(1.0);
        assertThat(AcceptanceRule.accepts(qa, 0)).isTrue();
    }

    @Test
    void accepts_reuseFailingItsProtectedSpanGate_notAccepted() {
        final CheckResult spanGate =
                CheckResult.hardGateFailed(CheckName.LOCKED_TERM, "The locked rendering of ⟦g0⟧ is missing.");
        final QaResult qa = QaEvaluator.evaluate(List.of(spanGate), reuse("Hale nodded.", "Хейл кивнув."));

        assertThat(AcceptanceRule.accepts(qa, 0)).isFalse();
    }

    private static SoftCheckInput reuse(final String source, final String target) {
        return new SoftCheckInput(
                source,
                target,
                target,
                "en",
                "uk",
                ForeignPassagePolicy.KEEP,
                NamePolicy.TRANSLITERATE,
                null,
                List.of(),
                List.of());
    }

    private static QaResult qa(final double confidence, final boolean hardGateFailed, final boolean failedOutright) {
        final List<CheckResult> hardGates = hardGateFailed
                ? List.of(CheckResult.hardGateFailed(CheckName.PLACEHOLDER, "placeholder gate failed"))
                : List.of();
        return new QaResult(hardGates, List.of(), confidence, List.of(), failedOutright);
    }
}
