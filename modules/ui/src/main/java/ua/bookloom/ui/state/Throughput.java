package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The pace figures of a run.
 *
 * @param tokensPerSecond completion tokens per second over the recent drafts, or {@code null} before one is known
 * @param estimated {@code true} when a provider reported no usage for a call in the window and the pipeline estimated
 *     it, so the figure is shown with a leading {@code ~}
 * @param timeLeft the estimated time until the last pending segment is decided, or {@code null} while too few
 *     segments are decided to say
 * @param elapsed the time the run has been running, not counting paused spans
 */
public record Throughput(
        @Nullable Double tokensPerSecond,
        boolean estimated,
        @Nullable Duration timeLeft,
        Duration elapsed) {

    /** The figures of a run that has just started. */
    public static final Throughput EMPTY = new Throughput(null, false, null, Duration.ZERO);

    /** Rejects a missing elapsed time. */
    public Throughput {
        Objects.requireNonNull(elapsed, "elapsed");
    }
}
