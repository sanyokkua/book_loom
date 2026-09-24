package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for verification report outcomes. */
class VerificationReportTest {

    @Test
    void reportWithFailedStage_isNotPassed() {
        final VerificationReport report = new VerificationReport(List.of(
                outcome(VerificationStage.CONNECTION, StageStatus.PASSED),
                outcome(VerificationStage.MODELS, StageStatus.FAILED)));

        assertThat(report.isPassed()).isFalse();
    }

    @Test
    void reportWithoutFailedStages_isPassed() {
        final VerificationReport report = new VerificationReport(List.of(
                outcome(VerificationStage.CONNECTION, StageStatus.PASSED),
                outcome(VerificationStage.MODELS, StageStatus.SOFT_PASS),
                outcome(VerificationStage.INFERENCE, StageStatus.SKIPPED)));

        assertThat(report.isPassed()).isTrue();
    }

    @Test
    void stages_constructorCopiesCallerListAndBlocksMutation() {
        final StageOutcome stage = outcome(VerificationStage.CONNECTION, StageStatus.PASSED);
        final List<StageOutcome> stages = new ArrayList<>(List.of(stage));

        final VerificationReport report = new VerificationReport(stages);
        stages.add(outcome(VerificationStage.MODELS, StageStatus.FAILED));

        assertThat(report.stages()).containsExactly(stage);
        assertThatThrownBy(() -> report.stages().add(stage)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static StageOutcome outcome(VerificationStage stage, StageStatus status) {
        return new StageOutcome(stage, status, null, null);
    }
}
