package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;

/** A run's probe — the window's and the command line's — passes only when the server answers and lists the model. */
class ProviderProbeTest {

    private static final ModelSelection SELECTION = new ModelSelection("ollama", "gemma4:e4b");

    @Test
    void probe_connectionAndModelsPassed_answersTheirTime() {
        final Result<Duration> probed = ProviderProbe.of(
                        verifierAnswering(Result.ok(new VerificationReport(List.of(
                                stage(VerificationStage.CONNECTION, StageStatus.PASSED, null, 40),
                                stage(VerificationStage.MODELS, StageStatus.PASSED, null, 60))))),
                        SELECTION)
                .probe();

        assertThat(probed.data()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void probe_connectionFailed_answersThatStagesError() {
        final AppError refused = AppError.of(ErrorCode.unreachable, "Down", "Nothing is listening.");

        final Result<Duration> probed = ProviderProbe.of(
                        verifierAnswering(Result.ok(new VerificationReport(
                                List.of(stage(VerificationStage.CONNECTION, StageStatus.FAILED, refused, 5))))),
                        SELECTION)
                .probe();

        assertThat(probed.error()).isSameAs(refused);
    }

    @Test
    void probe_modelGoneWithoutAnError_answersUnreachable() {
        final Result<Duration> probed = ProviderProbe.of(
                        verifierAnswering(Result.ok(new VerificationReport(List.of(
                                stage(VerificationStage.CONNECTION, StageStatus.PASSED, null, 5),
                                stage(VerificationStage.MODELS, StageStatus.FAILED, null, 5))))),
                        SELECTION)
                .probe();

        assertThat(probed.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
    }

    private static ProviderVerifier verifierAnswering(final Result<VerificationReport> report) {
        return new ProviderVerifier() {
            @Override
            public Result<VerificationReport> verify(final ModelSelection selection, final VerificationPolicy policy) {
                assertThat(policy).isEqualTo(VerificationPolicy.CONNECTION_AND_MODELS);
                return report;
            }

            @Override
            public Result<VerificationReport> verifyConnection(final String providerId) {
                return report;
            }
        };
    }

    private static StageOutcome stage(
            final VerificationStage stage,
            final StageStatus status,
            final @Nullable AppError error,
            final long millis) {
        return new StageOutcome(stage, status, error, null, Duration.ofMillis(millis), null);
    }
}
