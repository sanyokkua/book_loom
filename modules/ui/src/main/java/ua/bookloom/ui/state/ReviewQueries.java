package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;

/**
 * The reads the review panel makes from the desk, each answering what the panel already shows when the desk could not
 * be read, so a failed read never blanks the panel. They block on the desk, so never call them on the FX thread.
 */
@Slf4j
final class ReviewQueries {

    private final ReviewDesk desk;

    ReviewQueries(final ReviewDesk desk) {
        this.desk = Objects.requireNonNull(desk, "desk");
    }

    /**
     * The flagged count, or {@code shown} when it could not be read; the suspicious count read with it is published, so
     * the Review button's second number follows what the person does at the desk.
     */
    int refreshed(final String projectId, final int shown, final LiveSection live) {
        final ReviewCounts counts = desk.counts(projectId).data();
        if (counts == null) {
            return shown;
        }
        live.publishSuspicious(counts.suspicious());
        return counts.flagged();
    }

    /** The flagged count, or {@code shown} when it could not be read. */
    int flagged(final String projectId, final int shown) {
        final ReviewCounts counts = desk.counts(projectId).data();
        return counts == null ? shown : counts.flagged();
    }

    /** The segments matching the filter, or null when the list could not be read. */
    @Nullable
    List<SegmentView> list(final String projectId, final ReviewFilter filter) {
        final List<SegmentView> views = desk.queue(projectId, filter).data();
        if (views == null) {
            log.debug("the {} list could not be read: it is left as it was", filter);
        }
        return views;
    }

    /** The segment's current view, or null when it could not be read. */
    @Nullable
    SegmentView segment(final String projectId, final String segmentId) {
        final SegmentView view = desk.segment(projectId, segmentId).data();
        if (view == null) {
            log.debug("segment {} could not be read: the selection is left as it was", segmentId);
        }
        return view;
    }
}
