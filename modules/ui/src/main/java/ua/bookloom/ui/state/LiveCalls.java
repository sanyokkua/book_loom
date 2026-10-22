package ua.bookloom.ui.state;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;

/**
 * The live panel's two calls and what is known of their segments.
 *
 * @param current the call in flight, else the newest finished one, or {@code null} before the first call
 * @param previous the call before {@code current}, or {@code null} when there is none
 * @param segments the target and decision of the segments the two calls are about, by segment id
 * @param asOf the moment a waiting call's clock reads, cut to whole seconds, or {@code null} when nothing waits; it
 *     lets the screen show a running timer from published values alone
 * @param rows the segments of the chunk being translated, in reading order; empty for work that translates no chunk
 * @param recent the last calls of the run, oldest first, at most {@value LiveCallState#MAX_RECENT}
 * @param summaryVersion the version of the rolling summary the run last refreshed, zero before any
 */
public record LiveCalls(
        @Nullable CallSnapshot current,
        @Nullable CallSnapshot previous,
        Map<String, SegmentLive> segments,
        @Nullable Instant asOf,
        List<LiveRow> rows,
        List<CallSnapshot> recent,
        int summaryVersion) {

    /** The panel of a run that has made no call. */
    public static final LiveCalls EMPTY = new LiveCalls(null, null, Map.of(), null);

    /** Copies the segment map and the lists; a list that is already unmodifiable is kept, so equality is cheap. */
    public LiveCalls {
        segments = Map.copyOf(Objects.requireNonNull(segments, "segments"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        recent = List.copyOf(Objects.requireNonNull(recent, "recent"));
    }

    /**
     * The calls of work that keeps no rows, no history and no summary.
     *
     * @param current the call in flight, or {@code null}
     * @param previous the call before it, or {@code null}
     * @param segments what is known of their segments, by id
     * @param asOf the moment a waiting call's clock reads, or {@code null}
     */
    public LiveCalls(
            final @Nullable CallSnapshot current,
            final @Nullable CallSnapshot previous,
            final Map<String, SegmentLive> segments,
            final @Nullable Instant asOf) {
        this(current, previous, segments, asOf, List.of(), List.of(), 0);
    }
}
