package ua.bookloom.pipeline;

import com.google.inject.Inject;
import java.time.Clock;
import java.util.Objects;
import ua.bookloom.api.project.IdSource;

/**
 * What paces a run and names it: its clock, its id source, the timer its recovery waits pass by, the cadence of its
 * progress ticks and where its first batch size comes from. Tests script every one of them.
 *
 * @param clock the run's time source
 * @param idSource where the job id comes from
 * @param timer the timer a recovery wait passes by
 * @param ticks how progress ticks are scheduled
 * @param batchSize the size of the first batch, or {@link #FROM_PROFILE} to take the run model's profile size
 */
public record RunPacing(Clock clock, IdSource idSource, RecoveryTimer timer, RunTicks ticks, int batchSize) {

    /** The {@code batchSize} that asks for the model profile's size: the batch size belongs to the model. */
    public static final int FROM_PROFILE = 0;

    /** Rejects a missing collaborator. */
    public RunPacing {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(idSource, "idSource");
        Objects.requireNonNull(timer, "timer");
        Objects.requireNonNull(ticks, "ticks");
    }

    /** The pacing of a real run: daemon ticks and the first batch at the model profile's size. */
    @Inject
    public RunPacing(final Clock clock, final IdSource idSource, final RecoveryTimer timer) {
        this(clock, idSource, timer, RunTicks.DAEMON, FROM_PROFILE);
    }
}
