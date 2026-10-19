package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;

/**
 * One entry of the flagged queue the review panel lists.
 *
 * @param segmentId the segment's stable id
 * @param locator the human-readable locator the person reads
 * @param findings the QA findings recorded against the segment; copied
 * @param judgeScore the judge's score, or {@code null} when the judge did not run
 */
public record FlaggedRow(
        String segmentId,
        String locator,
        List<QaFinding> findings,
        @Nullable Double judgeScore) {

    /** Rejects a missing identity and takes an unmodifiable copy of the findings. */
    public FlaggedRow {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    /**
     * The row for a flagged segment.
     *
     * @param view a segment view read from the review desk
     * @return the row naming it by its locator
     */
    public static FlaggedRow of(final SegmentView view) {
        Objects.requireNonNull(view, "view");
        return new FlaggedRow(view.segmentId(), view.locator(), view.findings(), view.judgeScore());
    }
}
