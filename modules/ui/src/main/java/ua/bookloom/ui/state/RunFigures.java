package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.JobProgress;

/**
 * The figures a run shows, derived from an engine snapshot.
 *
 * <p>The snapshot carries neither a total nor a field called remaining, so both are derived here, once. The section
 * and chunk positions are carried for display only and feed no count: moving to a later chapter must not move the bar.
 *
 * @param autoAccepted segments accepted without repair
 * @param repaired segments accepted after at least one repair round
 * @param flagged segments flagged so far
 * @param remaining segments not decided yet
 * @param total auto-accepted plus repaired plus kept as is plus flagged plus remaining
 * @param fraction decided segments over the total, zero when the total is zero
 * @param section the 1-based body unit being translated, zero before the first
 * @param sections how many body units the book has
 * @param chunk the 1-based chunk within the section
 * @param chunks how many chunks the section has
 * @param keptVerbatim segments kept as they are with no model call (numbers, symbols); decided, so counted in
 *     {@code total} and {@code fraction}, but in neither {@code autoAccepted} nor {@code repaired}
 */
public record RunFigures(
        int autoAccepted,
        int repaired,
        int flagged,
        int remaining,
        int total,
        double fraction,
        int section,
        int sections,
        int chunk,
        int chunks,
        int keptVerbatim) {

    private static final int PERCENT = 100;

    /** The figures of a run that has not counted anything. */
    public static final RunFigures EMPTY = new RunFigures(0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0, 0);

    /**
     * Derives the figures from a snapshot.
     *
     * @param progress the engine's latest snapshot
     * @return the derived figures; the fraction is 0.0 rather than NaN when the snapshot counts nothing
     */
    public static RunFigures from(final JobProgress progress) {
        Objects.requireNonNull(progress, "progress");
        final int decided =
                progress.autoAccepted() + progress.repairedAccepted() + progress.keptVerbatim() + progress.flagged();
        final int total = decided + progress.pending();
        final double fraction = total == 0 ? 0.0 : (double) decided / total;
        return new RunFigures(
                progress.autoAccepted(),
                progress.repairedAccepted(),
                progress.flagged(),
                progress.pending(),
                total,
                fraction,
                progress.section(),
                progress.sections(),
                progress.chunk(),
                progress.chunks(),
                progress.keptVerbatim());
    }

    /**
     * The same figures with another flagged count, for when the review desk's stored count replaces the run's.
     *
     * @param count the segments flagged now, not negative
     * @return the figures with that flagged count; every other figure is as it was
     */
    public RunFigures withFlagged(final int count) {
        return new RunFigures(
                autoAccepted,
                repaired,
                count,
                remaining,
                total,
                fraction,
                section,
                sections,
                chunk,
                chunks,
                keptVerbatim);
    }

    /**
     * Segments accepted, with or without repair, and those kept as they are.
     *
     * @return auto-accepted plus repaired plus kept as is
     */
    public int accepted() {
        return autoAccepted + repaired + keptVerbatim;
    }

    /**
     * The decided share as a whole percent, the one figure the title bar and the progress card both show. It is rounded
     * down, so 100% means every segment is decided and two places reading the same figures never differ by a point.
     *
     * @return the percent from 0 to 100; zero when the total is zero
     */
    public int percent() {
        return total == 0 ? 0 : (int) ((long) (total - remaining) * PERCENT / total);
    }
}
