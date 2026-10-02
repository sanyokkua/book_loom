package ua.bookloom.api.pipeline;

import java.time.Duration;
import ua.bookloom.api.Result;

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
}
