package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ScriptedProviderVerifier;

/** A window run's probe passes only when the server answers and still lists the run's model. */
class ProviderProbesTest {

    private static final ModelSelection SELECTION = new ModelSelection("ollama", "gemma4:e4b");

    @Test
    void probe_connectionAndModelsPassed_answersTheirTime() {
        final ScriptedProviderVerifier verifier =
                ScriptedProviderVerifier.returning(Result.ok(new VerificationReport(List.of(
                        stage(VerificationStage.CONNECTION, StageStatus.PASSED, null, 40),
                        stage(VerificationStage.MODELS, StageStatus.PASSED, null, 60)))));

        final Result<Duration> probed = ProviderProbes.of(verifier, SELECTION).probe();

        assertThat(probed.data()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void probe_connectionFailed_answersThatStagesError() {
        final AppError refused = AppError.of(ErrorCode.unreachable, "Down", "Nothing is listening.");
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(Result.ok(
                new VerificationReport(List.of(stage(VerificationStage.CONNECTION, StageStatus.FAILED, refused, 5)))));

        final Result<Duration> probed = ProviderProbes.of(verifier, SELECTION).probe();

        assertThat(probed.error()).isSameAs(refused);
    }

    @Test
    void probe_modelGoneWithoutAnError_answersUnreachable() {
        final ScriptedProviderVerifier verifier =
                ScriptedProviderVerifier.returning(Result.ok(new VerificationReport(List.of(
                        stage(VerificationStage.CONNECTION, StageStatus.PASSED, null, 5),
                        stage(VerificationStage.MODELS, StageStatus.FAILED, null, 5)))));

        final Result<Duration> probed = ProviderProbes.of(verifier, SELECTION).probe();

        assertThat(probed.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
    }

    private static StageOutcome stage(
            final VerificationStage stage,
            final StageStatus status,
            final @Nullable AppError error,
            final long millis) {
        return new StageOutcome(stage, status, error, null, Duration.ofMillis(millis), null);
    }
}
