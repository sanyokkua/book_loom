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

    /**
     * What the desk opens on: the list it shows, the segment selected in it, and the filter that list belongs to.
     *
     * @param filter the filter listed, {@link ReviewFilter#ALL_SEGMENTS} when the named segment is only listed there
     * @param views the list, in document order
     * @param segment the segment to select, or {@code null} when the list is empty
     */
    record Opened(
            ReviewFilter filter,
            List<SegmentView> views,
            @Nullable SegmentView segment) {}

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

    /**
     * Reads the list the desk opens on and the segment to select: the named one when the list holds it, else the first.
     * A segment the filter does not list (a pause after an accepted segment) is looked for under All segments, which
     * then becomes the filter, when {@code widen} allows it.
     *
     * @return what to open on, or {@code null} when the list could not be read
     */
    @Nullable
    Opened opened(
            final String projectId, final ReviewFilter filter, final @Nullable String segmentId, final boolean widen) {
        final List<SegmentView> views = list(projectId, filter);
        if (views == null) {
            return null;
        }
        if (widen && segmentId != null && !holds(views, segmentId) && filter != ReviewFilter.ALL_SEGMENTS) {
            final List<SegmentView> all = list(projectId, ReviewFilter.ALL_SEGMENTS);
            if (all != null && holds(all, segmentId)) {
                log.debug("segment {} is not listed under {}: opening All segments", segmentId, filter);
                return new Opened(ReviewFilter.ALL_SEGMENTS, all, segment(projectId, segmentId));
            }
        }
        final String chosen = segmentId != null && holds(views, segmentId)
                ? segmentId
                : views.isEmpty() ? null : views.getFirst().segmentId();
        log.debug("the desk opens on {} of {} listed under {}", chosen, views.size(), filter);
        return new Opened(filter, views, chosen == null ? null : segment(projectId, chosen));
    }

    private static boolean holds(final List<SegmentView> views, final String segmentId) {
        return views.stream().anyMatch(view -> view.segmentId().equals(segmentId));
    }
}
