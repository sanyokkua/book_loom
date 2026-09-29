package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.judge.JudgeFinding;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaEvaluator;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.qa.SoftCheckInput;

/**
 * {@link AcceptanceRule#accepts}: every condition must hold at once, the verdict string never decides, and τ/τ_judge
 * come only from the review mode ({@code specs/quality-gates/spec.md} "Accept a segment only by the acceptance
 * rule").
 */
class AcceptanceRuleTest {

    private static final String SEGMENT_ID = "s1";

    @ParameterizedTest
    @CsvSource({"UNATTENDED,true", "ASSISTED,false", "MANUAL,false"})
    void accepts_confidenceAtUnattendedThreshold_acceptedOnlyInUnattended(
            final ReviewMode mode, final boolean expected) {
        assertThat(AcceptanceRule.accepts(passingQa(0.70), null, SEGMENT_ID, mode.threshold()))
                .isEqualTo(expected);
    }

    @Test
    void accepts_verdictFindingOnOneSegment_keepsOnlyThatSegmentOut() {
        final QaResult qa = passingQa(0.95);
        final JudgeVerdict verdict = new JudgeVerdict(
                0.82,
                "accept",
                List.of(new JudgeFinding("s2", "omission", Severity.MEDIUM, "drops the second clause")),
                List.of(),
                true);
        final double tau = ReviewMode.ASSISTED.threshold();
        assertThat(AcceptanceRule.accepts(qa, verdict, "s1", tau)).isTrue();
        assertThat(AcceptanceRule.accepts(qa, verdict, "s3", tau)).isTrue();
        assertThat(AcceptanceRule.accepts(qa, verdict, "s2", tau)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0.70,accept,false", "0.80,revise,true"})
    void accepts_verdictString_neverDecides(final double score, final String verdictString, final boolean expected) {
        final QaResult qa = passingQa(0.95);
        final JudgeVerdict verdict = new JudgeVerdict(score, verdictString, List.of(), List.of(), true);
        assertThat(AcceptanceRule.accepts(qa, verdict, SEGMENT_ID, ReviewMode.ASSISTED.threshold()))
                .isEqualTo(expected);
    }

    @Test
    void accepts_lowSeverityFinding_doesNotBlock() {
        final QaResult qa = passingQa(0.95);
        final JudgeVerdict verdict = new JudgeVerdict(
                0.80,
                "revise",
                List.of(new JudgeFinding(SEGMENT_ID, "fluency", Severity.LOW, "a bit stiff")),
                List.of(),
                true);
        assertThat(AcceptanceRule.accepts(qa, verdict, SEGMENT_ID, ReviewMode.ASSISTED.threshold()))
                .isTrue();
    }

    @Test
    void accepts_unreadableVerdict_acceptsNothingEvenWithAHighScore() {
        final QaResult qa = passingQa(1.0);
        final JudgeVerdict verdict = new JudgeVerdict(0.95, null, List.of(), List.of(), false);
        assertThat(AcceptanceRule.accepts(qa, verdict, SEGMENT_ID, ReviewMode.ASSISTED.threshold()))
                .isFalse();
    }

    @Test
    void accepts_hardGateFailed_neverAccepted() {
        final QaResult qa = hardGateFailedQa(1.0);
        final JudgeVerdict verdict = new JudgeVerdict(0.95, "accept", List.of(), List.of(), true);
        assertThat(AcceptanceRule.accepts(qa, verdict, SEGMENT_ID, ReviewMode.ASSISTED.threshold()))
                .isFalse();
    }

    @Test
    void accepts_failedOutrightSoftCheck_blocksAWellJudgedSegment() {
        final QaResult qa = failedOutrightQa(0.75);
        final JudgeVerdict verdict = new JudgeVerdict(0.95, "accept", List.of(), List.of(), true);
        assertThat(AcceptanceRule.accepts(qa, verdict, SEGMENT_ID, ReviewMode.ASSISTED.threshold()))
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(ReviewMode.class)
    void accepts_judgeOffHighConfidence_acceptedInEveryMode(final ReviewMode mode) {
        assertThat(AcceptanceRule.accepts(passingQa(0.875), null, SEGMENT_ID, mode.threshold()))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"0.85,true", "0.849,false"})
    void accepts_manualThresholdEdge_toleranceAppliesOnlyAtExactTau(final double confidence, final boolean expected) {
        assertThat(AcceptanceRule.accepts(passingQa(confidence), null, SEGMENT_ID, ReviewMode.MANUAL.threshold()))
                .isEqualTo(expected);
    }

    // The quality dial (here Max) never moves τ or τ_judge: both stay the review mode's own 0.60 in Unattended.
    @ParameterizedTest
    @CsvSource({"0.60,true", "0.599,false"})
    void accepts_maxDialAtUnattendedReviewMode_tauStillComesFromReviewModeAlone(
            final double score, final boolean expected) {
        final DialParameters max = DialParameters.of(QualityDial.MAX);
        assertThat(max.judge()).isTrue();
        final QaResult qa = passingQa(1.0);
        final JudgeVerdict verdict = new JudgeVerdict(score, "accept", List.of(), List.of(), true);
        assertThat(AcceptanceRule.accepts(qa, verdict, SEGMENT_ID, ReviewMode.UNATTENDED.threshold()))
                .isEqualTo(expected);
    }

    @Test
    void accepts_realFailedLengthCheck_blocksRegardlessOfConfidence() {
        final CheckResult failedLength = CheckResult.fail(CheckName.LENGTH, "length ratio 0.47 outside [0.7,1.8]");
        final QaResult qa = new QaResult(List.of(), List.of(failedLength), 0.95, List.of(failedLength.finding()), true);
        assertThat(AcceptanceRule.accepts(qa, null, SEGMENT_ID, ReviewMode.UNATTENDED.threshold()))
                .isFalse();
    }

    // 0.30*1 (glossary skip) + 0.25*0.2 (length) + 0.20*0.5 (script) + 0.15*1 (echo skip) + 0.10*1 (repetition
    // skip) = 0.70, the confidence "One confidence, three modes" is worked from.
    @Test
    void accepts_confidenceBuiltFromRealScriptAndLengthMargins_matchesTheWorkedSum() {
        final List<CheckResult> soft = List.of(
                CheckResult.pass(CheckName.SCRIPT, 0.5),
                CheckResult.skip(CheckName.ECHO),
                CheckResult.skip(CheckName.REPETITION),
                CheckResult.pass(CheckName.LENGTH, 0.2),
                CheckResult.skip(CheckName.GLOSSARY));
        final QaResult qa = new QaResult(List.of(), soft, 0.70, List.of(), false);
        assertThat(AcceptanceRule.accepts(qa, null, SEGMENT_ID, ReviewMode.UNATTENDED.threshold()))
                .isTrue();
        assertThat(AcceptanceRule.accepts(qa, null, SEGMENT_ID, ReviewMode.ASSISTED.threshold()))
                .isFalse();
        assertThat(AcceptanceRule.accepts(qa, null, SEGMENT_ID, ReviewMode.MANUAL.threshold()))
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(ReviewMode.class)
    void acceptsReuse_contextMatchedTargetAtFullConfidence_acceptedInEveryMode(final ReviewMode mode) {
        final QaResult qa = QaEvaluator.evaluate(List.of(), reuse("Yes.", "Так."));

        assertThat(qa.confidence()).isEqualTo(1.0);
        assertThat(AcceptanceRule.acceptsReuse(qa, mode.threshold())).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ReviewMode.class)
    void acceptsReuse_targetFailingItsProtectedSpanGate_notAccepted(final ReviewMode mode) {
        final CheckResult spanGate =
                CheckResult.hardGateFailed(CheckName.LOCKED_TERM, "The locked rendering of ⟦g0⟧ is missing.");
        final QaResult qa = QaEvaluator.evaluate(List.of(spanGate), reuse("Hale nodded.", "Хейл кивнув."));

        assertThat(AcceptanceRule.acceptsReuse(qa, mode.threshold())).isFalse();
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

    private static QaResult passingQa(final double confidence) {
        return new QaResult(List.of(), List.of(), confidence, List.of(), false);
    }

    private static QaResult failedOutrightQa(final double confidence) {
        return new QaResult(List.of(), List.of(), confidence, List.of(), true);
    }

    private static QaResult hardGateFailedQa(final double confidence) {
        return new QaResult(
                List.of(CheckResult.hardGateFailed(CheckName.PLACEHOLDER, "placeholder gate failed")),
                List.of(),
                confidence,
                List.of(),
                false);
    }
}
