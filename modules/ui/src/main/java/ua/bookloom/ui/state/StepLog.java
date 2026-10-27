package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepsPlanned;

/**
 * The steps one piece of work announced, folded from its {@link StepsPlanned} and {@link StepChanged} events: what is
 * planned, each step's last state, and the share done, which never falls when work is added.
 *
 * <p>The names pass plans its stages as {@link StepKind#STAGE} group rows and counts its requests; a stage is counted
 * only by work that plans nothing else. A step started again under the same kind and index keeps its row.
 *
 * @param planned the steps planned of every kind but {@link StepKind#STAGE}; not negative
 * @param stages the {@link StepKind#STAGE} steps planned; not negative
 * @param estimated whether some planned count is a forecast the work may correct
 * @param reached the highest share done so far, between 0 and 1
 * @param entries the steps announced, in the order first seen
 */
public record StepLog(int planned, int stages, boolean estimated, double reached, List<StepEntry> entries) {

    /** The log of work that announced nothing. */
    public static final StepLog EMPTY = new StepLog(0, 0, false, 0, List.of());

    /** Freezes the entries and rejects negative counts. */
    public StepLog {
        if (planned < 0 || stages < 0) {
            throw new IllegalArgumentException(
                    "planned " + planned + " and stages " + stages + " must not be negative");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /**
     * Folds one event in.
     *
     * @param event any job event; only a {@link StepsPlanned} or a {@link StepChanged} changes the log
     * @return the log with the event folded in, or this log for any other event
     */
    public StepLog apply(final JobEvent event) {
        Objects.requireNonNull(event, "event");
        return switch (event) {
            case StepsPlanned plan ->
                plan.kind() == StepKind.STAGE
                        ? new StepLog(planned, stages + plan.added(), estimated || plan.estimated(), reached, entries)
                                .settled()
                        : new StepLog(planned + plan.added(), stages, estimated || plan.estimated(), reached, entries)
                                .settled();
            case StepChanged changed -> apply(changed, null);
            default -> this;
        };
    }

    /**
     * Folds one step change in, with what the step read and wrote.
     *
     * @param changed the step's new state
     * @param text what it read and wrote, or {@code null} when the work said nothing of it
     * @return the log with the step's row added or replaced
     */
    public StepLog apply(final StepChanged changed, final StepEntry.@Nullable StepText text) {
        Objects.requireNonNull(changed, "changed");
        final StepEntry entry = new StepEntry(
                changed.kind(),
                changed.label(),
                changed.index(),
                changed.state(),
                changed.itemRef(),
                changed.result(),
                text);
        final List<StepEntry> next = new ArrayList<>(entries);
        final int at = IntStream.range(0, next.size())
                .filter(i -> next.get(i).key().equals(entry.key()))
                .findFirst()
                .orElse(-1);
        if (at < 0) {
            next.add(entry);
        } else {
            next.set(at, entry);
        }
        return new StepLog(planned, stages, estimated, reached, next).settled();
    }

    /**
     * Whether the work announced anything.
     *
     * @return {@code true} while nothing is planned or announced, {@code false} otherwise
     */
    public boolean isEmpty() {
        return planned == 0 && stages == 0 && entries.isEmpty();
    }

    /**
     * The counted steps that have ended.
     *
     * @return the count, never negative
     */
    public int done() {
        return (int) counted().stream()
                .filter(entry -> !entry.isPending() && !entry.isRunning())
                .count();
    }

    /**
     * The counted steps planned or announced, whichever is more.
     *
     * @return the count, never negative
     */
    public int total() {
        return Math.max(isStageCounted() ? stages : planned, counted().size());
    }

    /**
     * The step the work is on: the one that started last and has not ended.
     *
     * @return the running step, or {@code null} when none runs
     */
    public @Nullable StepEntry running() {
        return entries.reversed().stream()
                .filter(StepEntry::isRunning)
                .findFirst()
                .orElse(null);
    }

    /**
     * The list a dialog shows: every step announced, then a row for each counted step planned and not started.
     *
     * @return the rows in order; never null
     */
    public List<StepEntry> rows() {
        final List<StepEntry> counted = counted();
        final int waiting = Math.max(0, total() - counted.size());
        final StepKind kind = isStageCounted() ? StepKind.STAGE : StepKind.REQUEST;
        final List<StepEntry> rows = new ArrayList<>(entries);
        IntStream.range(0, waiting)
                .mapToObj(n -> new StepEntry(kind, "", counted.size() + n, null, null, null, null))
                .forEach(rows::add);
        return rows;
    }

    private boolean isStageCounted() {
        return planned == 0;
    }

    private List<StepEntry> counted() {
        final boolean stagesCount = isStageCounted();
        return entries.stream()
                .filter(entry -> (entry.kind() == StepKind.STAGE) == stagesCount)
                .toList();
    }

    private StepLog settled() {
        final int all = total();
        final double share = all == 0 ? 0 : (double) done() / all;
        return share > reached ? new StepLog(planned, stages, estimated, share, entries) : this;
    }
}
