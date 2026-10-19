package ua.bookloom.ui.state;

import java.time.Instant;
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
 */
public record LiveCalls(
        @Nullable CallSnapshot current,
        @Nullable CallSnapshot previous,
        Map<String, SegmentLive> segments,
        @Nullable Instant asOf) {

    /** The panel of a run that has made no call. */
    public static final LiveCalls EMPTY = new LiveCalls(null, null, Map.of(), null);

    /** Copies the segment map. */
    public LiveCalls {
        segments = Map.copyOf(Objects.requireNonNull(segments, "segments"));
    }
}
