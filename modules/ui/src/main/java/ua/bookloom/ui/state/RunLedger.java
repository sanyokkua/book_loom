package ua.bookloom.ui.state;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;

/**
 * A run's {@link ProgressLedger} fed by its events, and the last figures handed on, so a tick publishes only what
 * moved. Not thread-safe: {@link RunSession} calls it under its publish lock.
 */
final class RunLedger {

    private final ProgressLedger ledger;
    private ProgressFigures.Figures shown = ProgressFigures.Figures.NONE;

    RunLedger(final RunClock clock) {
        this.ledger = new ProgressLedger(clock);
    }

    void planned(final StepsPlanned planned) {
        ledger.planned(planned.kind(), planned.added());
    }

    void changed(final StepChanged changed, final Instant now) {
        final StepResult result = changed.result();
        if (changed.state() == StepState.ENDED && result != null) {
            ledger.ended(changed.kind(), result, now);
        }
    }

    /**
     * Reads the ledger once the book's size is known.
     *
     * @param seen the run's latest snapshot, or {@code null} before the first
     * @param now the moment of the read
     * @return the figures; none before the first snapshot, since a chunk's own plan must not set the bar's mark
     */
    ProgressFigures.Figures read(final @Nullable JobProgress seen, final Instant now) {
        if (seen == null) {
            return ProgressFigures.Figures.NONE;
        }
        final RunFigures counts = RunFigures.from(seen);
        ledger.seed(counts.total(), counts.total() - counts.remaining());
        return ledger.read(now);
    }

    /**
     * Whether figures differ from the ones last handed on; remembers them when they do.
     *
     * @param steps the figures just read
     * @return {@code true} when they are new and should be published
     */
    boolean isNew(final ProgressFigures.Figures steps) {
        final boolean fresh = !steps.equals(shown);
        shown = steps;
        return fresh;
    }
}
