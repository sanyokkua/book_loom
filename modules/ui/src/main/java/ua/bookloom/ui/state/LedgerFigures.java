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
 * work: its first read starts the clock. When the log changes what it times (stages, then requests) the ledger starts
 * again, since the two counts must not be added; work that plans both is counted by its stages and timed by its
 * requests. FX thread only.
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
        final int newlyPlanned =
                (kind == StepKind.STAGE ? steps.total() : steps.timedTotal()) - planned.getOrDefault(kind, 0);
        if (newlyPlanned > 0) {
            current.planned(kind, newlyPlanned);
            planned.merge(kind, newlyPlanned, Integer::sum);
        }
        final int newlyDone = (kind == StepKind.STAGE ? steps.done() : steps.timedDone()) - done.getOrDefault(kind, 0);
        if (newlyDone > 0) {
            current.ended(kind, StepResult.DONE, newlyDone, now);
            done.merge(kind, newlyDone, Integer::sum);
        }
        final Figures read = countedByStages(steps, current.read(now));
        heldFraction = Math.max(heldFraction, read.fraction());
        return read.fraction() >= heldFraction
                ? read
                : read.holding(Figures.percentOf(heldFraction, false), heldFraction);
    }

    // Work that plans stages and requests (the names pass) is counted by its stages, which the person sees as rows, and
    // timed by its requests, whose seconds the ledger measures; the stages differ too much for a mean.
    private static Figures countedByStages(final StepLog steps, final Figures requests) {
        if (steps.stages() == 0 || steps.planned() == 0) {
            return requests;
        }
        final Figures stages = Figures.ofCount(steps.done(), steps.total());
        log.trace(
                "progress figures: {} of {} stages, time left {} from the requests",
                stages.done(),
                stages.total(),
                requests.left());
        return new Figures(
                stages.percent(), stages.done(), stages.total(), requests.left(), stages.fraction(), requests.kinds());
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
