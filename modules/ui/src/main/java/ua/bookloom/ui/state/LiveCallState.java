package ua.bookloom.ui.state;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;

/**
 * The two calls of the live panel, worked out from the call snapshots, and the targets of their segments.
 *
 * <p>One call arrives many times under one id: waiting, then answered or failed, then once more for each outcome noted
 * on it. An update of either held call replaces it in place; a call newer than both pushes the current one back and
 * drops the one before. Calls run one at a time, so call ids rise and the newest id is the current call. Not
 * thread-safe: {@link RunSession} calls it under its publish lock.
 */
@Slf4j
final class LiveCallState {

    /** A batch holds at most a handful of segments and two calls are shown, so older targets are surplus. */
    private static final int MAX_SEGMENTS = 64;

    private final Map<String, SegmentLive> segments = new LinkedHashMap<>();
    private @Nullable CallSnapshot current;
    private @Nullable CallSnapshot previous;

    void snapshot(final CallSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        final CallSnapshot now = current;
        final CallSnapshot before = previous;
        if (now == null || snapshot.callId() == now.callId()) {
            current = snapshot;
        } else if (snapshot.callId() > now.callId()) {
            previous = now;
            current = snapshot;
        } else if (before == null || snapshot.callId() >= before.callId()) {
            previous = snapshot;
        } else {
            log.debug("live calls: call {} is older than both held calls; ignored", snapshot.callId());
            return;
        }
        log.debug(
                "live calls: call {} {} ({} segments, {} outcomes), current {}, previous {}",
                snapshot.callId(),
                snapshot.state(),
                snapshot.segments().size(),
                snapshot.outcomes().size(),
                current == null ? "none" : current.callId(),
                previous == null ? "none" : previous.callId());
    }

    void drafted(final SegmentDrafted event) {
        log.debug("live calls: segment {} drafted", event.segmentId());
        final SegmentLive known = segments.get(event.segmentId());
        remember(
                event.segmentId(),
                new SegmentLive(
                        event.displayTarget(),
                        known == null ? null : known.judgeScore(),
                        known == null ? null : known.path()));
    }

    void decided(final SegmentDecided event) {
        final SegmentDetail detail = event.detail();
        final SegmentLive known = segments.get(event.segmentId());
        final String kept = known == null ? null : known.target();
        final String target = detail == null || detail.displayTarget() == null ? kept : detail.displayTarget();
        log.debug("live calls: segment {} decided {}", event.segmentId(), event.status());
        remember(
                event.segmentId(),
                new SegmentLive(
                        target, detail == null ? null : detail.judgeScore(), detail == null ? null : detail.path()));
    }

    /**
     * The panel as it reads at {@code now}.
     *
     * @param now the moment the panel is read
     * @return the two calls, their segments, and the waiting clock in whole seconds while the current call waits
     */
    LiveCalls view(final Instant now) {
        final boolean waits = current != null && current.state() == CallState.WAITING;
        return new LiveCalls(current, previous, segments, waits ? now.truncatedTo(ChronoUnit.SECONDS) : null);
    }

    private void remember(final String segmentId, final SegmentLive live) {
        segments.remove(segmentId);
        segments.put(segmentId, live);
        if (segments.size() > MAX_SEGMENTS) {
            segments.remove(segments.keySet().iterator().next());
        }
    }
}
