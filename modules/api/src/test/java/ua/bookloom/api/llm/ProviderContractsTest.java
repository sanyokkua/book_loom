package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/** Contract tests for the required components of provider and verification value types. */
class ProviderContractsTest {

    @SuppressWarnings("NullAway")
    @Test
    void modelInfo_nullId_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ModelInfo(null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void responseFormat_nullName_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ResponseFormat(null, "{}"));
    }

    @SuppressWarnings("NullAway")
    @Test
    void responseFormat_nullJsonSchema_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new ResponseFormat("draft", null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void stageOutcome_nullStage_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new StageOutcome(null, StageStatus.PASSED, null, null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void stageOutcome_nullStatus_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> new StageOutcome(VerificationStage.CONNECTION, null, null, null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void verificationReport_nullStages_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> new VerificationReport(null));
    }
}
