package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.JobProgress;

/**
 * The figures a run shows, derived from an engine snapshot.
 *
 * <p>The snapshot carries neither a total nor a field called remaining, so both are derived here, once. The section
 * columns are deliberately never read: moving to a later chapter must not move the bar.
 *
 * @param accepted segments accepted so far
 * @param flagged segments flagged so far
 * @param remaining segments not decided yet
 * @param total accepted plus flagged plus remaining
 * @param fraction decided segments over the total, zero when the total is zero
 */
public record RunFigures(int accepted, int flagged, int remaining, int total, double fraction) {

    /** The figures of a run that has not counted anything. */
    public static final RunFigures EMPTY = new RunFigures(0, 0, 0, 0, 0.0);

    /**
     * Derives the figures from a snapshot.
     *
     * @param progress the engine's latest snapshot
     * @return the derived figures; the fraction is 0.0 rather than NaN when the snapshot counts nothing
     */
    public static RunFigures from(final JobProgress progress) {
        Objects.requireNonNull(progress, "progress");
        final int total = progress.accepted() + progress.flagged() + progress.pending();
        final double fraction = total == 0 ? 0.0 : (double) (progress.accepted() + progress.flagged()) / total;
        return new RunFigures(progress.accepted(), progress.flagged(), progress.pending(), total, fraction);
    }
}
