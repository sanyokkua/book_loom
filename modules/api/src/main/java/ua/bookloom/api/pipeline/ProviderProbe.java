package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;

/**
 * Asks the run's provider whether it can answer again, before a run paused on a provider error resumes by itself. A
 * probe lists models and never infers, so it costs a person nothing and never queues behind a model call.
 */
@FunctionalInterface
public interface ProviderProbe {

    /** The probe of a run that has no provider to ask, such as a test model: the provider counts as reachable. */
    ProviderProbe ASSUME_REACHABLE = () -> Result.ok(Duration.ZERO);

    /**
     * Probes the provider once.
     *
     * @return how long the probe took when the provider answered and offers the run's model, or the error that says
     *     why it cannot be used yet
     */
    Result<Duration> probe();

    /**
     * The probe a run checks its provider with — the window's and the command line's alike: the connection and model
     * list stages, so it passes only when the server answers and still offers the run's model.
     *
     * @param verifier the port that checks a provider; never null
     * @param selection the run's provider and model; never null
     * @return the probe; never null
     */
    static ProviderProbe of(final ProviderVerifier verifier, final ModelSelection selection) {
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(selection, "selection");
        return () -> verifier.verify(selection, VerificationPolicy.CONNECTION_AND_MODELS)
                .flatMap(ProviderProbe::outcomeOf);
    }

    private static Result<Duration> outcomeOf(final VerificationReport report) {
        final Optional<StageOutcome> failed = report.stages().stream()
                .filter(stage -> stage.status() == StageStatus.FAILED)
                .findFirst();
        if (failed.isPresent()) {
            final AppError error = failed.get().error();
            return Result.err(
                    error != null
                            ? error
                            : AppError.of(
                                    ErrorCode.unreachable, "Provider not ready", "The provider check did not pass."));
        }
        return Result.ok(report.stages().stream()
                .map(StageOutcome::elapsed)
                .filter(Objects::nonNull)
                .reduce(Duration.ZERO, Duration::plus));
    }
}
