package ua.bookloom.ui.state;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;

/**
 * The two rows of the live panel, worked out from the segment announcements.
 *
 * <p>A chunk announces several segments before the first is decided, so the segments started and not yet decided are
 * kept in the order they started and the second row shows the newest of them. Not thread-safe: {@link RunSession}
 * calls it under its publish lock.
 */
@Slf4j
final class LiveChunkState {

    /** More undecided segments than one chunk can hold means a decision was lost; the oldest is dropped. */
    private static final int MAX_UNDECIDED = 32;

    private final boolean judged;
    private final Map<String, LiveRow> undecided = new LinkedHashMap<>();
    private @Nullable LiveRow lastDecided;

    LiveChunkState(final QualityDial dial) {
        this.judged = Objects.requireNonNull(dial, "dial") != QualityDial.FAST;
    }

    void started(final SegmentStarted event) {
        log.debug("live panel: segment {} started", event.segmentId());
        log.trace("live panel: {} source '{}'", event.locator(), event.displaySource());
        undecided.remove(event.segmentId());
        undecided.put(
                event.segmentId(),
                new LiveRow(event.segmentId(), event.locator(), event.displaySource(), null, null, null, true, false));
        if (undecided.size() > MAX_UNDECIDED) {
            final String oldest = undecided.keySet().iterator().next();
            log.debug("live panel: dropping segment {}, which was never decided", oldest);
            undecided.remove(oldest);
        }
    }

    void drafted(final SegmentDrafted event) {
        final LiveRow row = undecided.get(event.segmentId());
        if (row == null) {
            log.debug("live panel: a draft for segment {}, which is not in progress", event.segmentId());
            return;
        }
        log.debug("live panel: segment {} drafted, awaiting judge {}", event.segmentId(), judged);
        log.trace("live panel: {} draft '{}'", row.locator(), event.displayTarget());
        undecided.put(
                event.segmentId(),
                new LiveRow(
                        row.segmentId(),
                        row.locator(),
                        row.sourceText(),
                        event.displayTarget(),
                        null,
                        null,
                        false,
                        judged));
    }

    void decided(final SegmentDecided event) {
        final LiveRow row = undecided.remove(event.segmentId());
        if (row == null) {
            log.debug("live panel: segment {} was decided without being announced", event.segmentId());
            return;
        }
        final SegmentDetail detail = event.detail();
        log.debug("live panel: segment {} decided {}", event.segmentId(), event.status());
        lastDecided = new LiveRow(
                row.segmentId(),
                row.locator(),
                row.sourceText(),
                row.targetText(),
                detail == null ? null : detail.judgeScore(),
                detail == null ? null : detail.path(),
                false,
                false);
    }

    LiveRows rows() {
        LiveRow current = null;
        for (final LiveRow row : undecided.values()) {
            current = row;
        }
        return new LiveRows(lastDecided, current);
    }
}
