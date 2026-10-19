package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.SegmentPath;

/**
 * One entry of the review panel's list.
 *
 * @param segmentId the segment's stable id, which every action names
 * @param locator the human-readable locator the person reads
 * @param badge the main finding of a flagged segment, or {@code null} when it is not flagged or has no finding
 * @param keptAsSource whether the record was kept as source by choice, so the row says so
 */
public record ReviewRow(
        String segmentId, String locator, @Nullable FindingBadge badge, boolean keptAsSource) {

    /** Rejects a missing identity. */
    public ReviewRow {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
    }

    /**
     * The row for a segment the desk listed.
     *
     * @param view a view read from the review desk
     * @return its row; only a flagged segment gets a badge
     */
    public static ReviewRow of(final SegmentView view) {
        Objects.requireNonNull(view, "view");
        final FindingBadge badge = view.status() == SegmentStatus.FLAGGED
                ? FindingBadge.of(view.findings(), view.judgeScore()).orElse(null)
                : null;
        return new ReviewRow(view.segmentId(), view.locator(), badge, view.path() == SegmentPath.SOURCE_KEPT);
    }
}
