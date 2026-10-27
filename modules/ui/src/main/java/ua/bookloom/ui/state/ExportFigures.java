package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.project.ExportRecord;

/**
 * What the Export screen's tiles and banners read: the project's live counts and, once a book was written and checked,
 * the stored record of that export. A segment kept as source by choice is no work left to do, so it counts in neither
 * the translated figure nor the figure it is read against.
 *
 * @param counts the review desk's counts now
 * @param last the project's last written export, or null when none was
 */
public record ExportFigures(ReviewCounts counts, @Nullable ExportRecord last) {

    /** Rejects missing counts. */
    public ExportFigures {
        Objects.requireNonNull(counts, "counts");
    }

    /**
     * The segments a book written now carries a translation for.
     *
     * @return zero or more
     */
    public int translated() {
        return Math.max(0, counts.total() - counts.sourceKept() - counts.pending() - counts.flaggedWithoutTarget());
    }

    /**
     * The segments that could carry a translation.
     *
     * @return zero or more
     */
    public int ofTotal() {
        return Math.max(0, counts.total() - counts.sourceKept());
    }

    /**
     * Whether a book written now would keep some of its text in the original language.
     *
     * @return {@code true} if fewer segments are translated than could be, {@code false} otherwise
     */
    public boolean isPartial() {
        return translated() < ofTotal();
    }

    /**
     * Whether nothing is translated yet, so there is nothing to export.
     *
     * @return {@code true} if no segment carries a translation, {@code false} otherwise
     */
    public boolean isNothing() {
        return translated() == 0;
    }
}
