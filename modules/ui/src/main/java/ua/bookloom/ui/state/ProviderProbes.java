package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.pipeline.ProviderProbe;

/**
 * The probe a window run checks its provider with before resuming by itself: the connection and the model list, which
 * says both that the server answers and that it still offers the run's model, without asking the model anything.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProviderProbes {

    /**
     * The probe of one provider and model.
     *
     * @param verifier the port that checks a provider
     * @param selection the run's provider and model
     * @return the probe; never null
     */
    static ProviderProbe of(final ProviderVerifier verifier, final ModelSelection selection) {
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(selection, "selection");
        return () -> {
            log.debug("probing provider {} for model {}", selection.providerId(), selection.modelId());
            final Result<VerificationReport> report =
                    verifier.verify(selection, VerificationPolicy.CONNECTION_AND_MODELS);
            if (report.isErr()) {
                return Result.err(Objects.requireNonNull(report.error(), "error"));
            }
            return outcomeOf(Objects.requireNonNull(report.data(), "report"));
        };
    }

    private static Result<Duration> outcomeOf(final VerificationReport report) {
        final Optional<StageOutcome> failed = report.stages().stream()
                .filter(stage -> stage.status() == StageStatus.FAILED)
                .findFirst();
        if (failed.isPresent()) {
            final AppError error = failed.get().error();
            log.debug("provider probe failed at stage {}", failed.get().stage());
            return Result.err(
                    error != null
                            ? error
                            : AppError.of(
                                    ErrorCode.unreachable, "Provider not ready", "The provider check did not pass."));
        }
        final Duration took = report.stages().stream()
                .map(StageOutcome::elapsed)
                .filter(Objects::nonNull)
                .reduce(Duration.ZERO, Duration::plus);
        return Result.ok(took);
    }
}
