package ua.bookloom.api.pipeline;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
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
     * Probes the provider once, asking what the error the run paused on needs: a server still loading its model is
     * asked whether it is done before its model list.
     *
     * @param pausedOn the error the run paused on; never null
     * @return as {@link #probe()}
     */
    default Result<Duration> probe(final AppError pausedOn) {
        return probe();
    }

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
        return new ProviderProbe() {
            @Override
            public Result<Duration> probe() {
                return checked(verifier, selection, VerificationPolicy.CONNECTION_AND_MODELS);
            }

            @Override
            public Result<Duration> probe(final AppError pausedOn) {
                Objects.requireNonNull(pausedOn, "pausedOn");
                final boolean loading = FailureReason.in(pausedOn.details())
                        .filter(FailureReason.MODEL_LOADING::equals)
                        .isPresent();
                return checked(
                        verifier,
                        selection,
                        loading ? VerificationPolicy.HEALTH_AND_MODELS : VerificationPolicy.CONNECTION_AND_MODELS);
            }
        };
    }

    private static Result<Duration> checked(
            final ProviderVerifier verifier, final ModelSelection selection, final VerificationPolicy policy) {
        return verifier.verify(selection, policy).flatMap(ProviderProbe::outcomeOf);
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
