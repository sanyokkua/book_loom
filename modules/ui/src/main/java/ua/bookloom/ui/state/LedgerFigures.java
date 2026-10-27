package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;

/**
 * The figures of one progress dialog, read from the steps its work announced through a {@link ProgressLedger}.
 *
 * <p>A dialog is handed the log of its steps, not the moment each step ended, so the time a step took is the time
 * between the reads that saw it end, shared among the steps seen to end in one read. One reader serves one piece of
 * work: its first read starts the clock. When the log changes what it counts (stages, then requests) the ledger starts
 * again, since the two counts must not be added. FX thread only.
 */
@Slf4j
public final class LedgerFigures implements ProgressFigures {

    private @Nullable ProgressLedger ledger;
    private @Nullable StepKind counting;
    private final Map<StepKind, Integer> planned = new EnumMap<>(StepKind.class);
    private final Map<StepKind, Integer> done = new EnumMap<>(StepKind.class);
    private double heldFraction;

    @Override
    public Figures of(final StepLog steps, final Instant now) {
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(now, "now");
        if (steps.isEmpty()) {
            return Figures.NONE;
        }
        final StepKind kind = steps.planned() == 0 ? StepKind.STAGE : StepKind.REQUEST;
        final ProgressLedger current = ledgerFor(kind, now);
        final int newlyPlanned = steps.total() - planned.getOrDefault(kind, 0);
        if (newlyPlanned > 0) {
            current.planned(kind, newlyPlanned);
            planned.merge(kind, newlyPlanned, Integer::sum);
        }
        final int newlyDone = steps.done() - done.getOrDefault(kind, 0);
        if (newlyDone > 0) {
            current.ended(kind, StepResult.DONE, newlyDone, now);
            done.merge(kind, newlyDone, Integer::sum);
        }
        final Figures read = current.read(now);
        heldFraction = Math.max(heldFraction, read.fraction());
        return read.fraction() >= heldFraction
                ? read
                : read.holding(Figures.percentOf(heldFraction, false), heldFraction);
    }

    private ProgressLedger ledgerFor(final StepKind kind, final Instant now) {
        if (ledger == null || kind != counting) {
            log.debug("progress figures: counting {} steps, starting the ledger", kind);
            ledger = new ProgressLedger(new RunClock(now));
            counting = kind;
            planned.clear();
            done.clear();
        }
        return ledger;
    }
}
