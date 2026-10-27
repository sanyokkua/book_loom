package ua.bookloom.ui.state;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;

/**
 * The two calls of the live panel, worked out from the call snapshots, the targets of their segments, the run's rows
 * (the chunk being translated last, earlier chunks above it), the last {@value #MAX_RECENT} calls and the rolling summary's version.
 *
 * <p>One call arrives many times under one id: waiting, then answered or failed, then once more for each outcome noted
 * on it. An update of either held call replaces it in place; a call newer than both pushes the current one back and
 * drops the one before. Calls run one at a time, so call ids rise and the newest id is the current call. The history
 * list is rebuilt only when a call changes, so the views of two ticks share it and compare equal at once. Not
 * thread-safe: {@link RunSession} calls it under its publish lock, {@link CallFeed} on the FX thread.
 */
@Slf4j
final class LiveCallState {

    /** A batch holds at most a handful of segments and two calls are shown, so older targets are surplus. */
    private static final int MAX_SEGMENTS = 64;

    /** How many calls "All calls…" lists; each holds its full prompt, so the history is bounded. */
    static final int MAX_RECENT = 50;

    private final Map<String, SegmentLive> segments = new LinkedHashMap<>();
    private final ChunkRows rows = new ChunkRows();
    private final Deque<CallSnapshot> history = new ArrayDeque<>();
    private List<CallSnapshot> recent = List.of();
    private int summaryVersion;
    private @Nullable CallSnapshot current;
    private @Nullable CallSnapshot previous;

    void snapshot(final CallSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        remember(snapshot);
        rows.call(snapshot);
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

    // An update of a held call replaces it where it stands; a new one joins the end and pushes out the oldest.
    private void remember(final CallSnapshot snapshot) {
        final boolean replaced = history.removeIf(held -> held.callId() == snapshot.callId());
        history.addLast(snapshot);
        if (replaced) {
            final List<CallSnapshot> sorted = history.stream()
                    .sorted(Comparator.comparingLong(CallSnapshot::callId))
                    .toList();
            history.clear();
            history.addAll(sorted);
        }
        while (history.size() > MAX_RECENT) {
            history.removeFirst();
        }
        recent = List.copyOf(history);
    }

    void started(final SegmentStarted event) {
        rows.started(event);
    }

    void memory(final MemoryUpdated event) {
        if (event.kind() != MemoryKind.SUMMARY) {
            return;
        }
        try {
            summaryVersion = Integer.parseInt(event.label().trim());
        } catch (NumberFormatException notANumber) {
            summaryVersion++;
            log.debug("live calls: summary label {} is no version, counted as {}", event.label(), summaryVersion);
        }
        log.debug("live calls: rolling summary now at version {}", summaryVersion);
    }

    void drafted(final SegmentDrafted event) {
        rows.drafted(event);
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
        rows.decided(event);
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
     * @return the two calls, their segments, the waiting clock in whole seconds while the current call waits, the
     *     chunk's rows, the history and the summary's version
     */
    LiveCalls view(final Instant now) {
        final boolean waits = current != null && current.state() == CallState.WAITING;
        return new LiveCalls(
                current,
                previous,
                segments,
                waits ? now.truncatedTo(ChronoUnit.SECONDS) : null,
                rows.rows(),
                recent,
                summaryVersion);
    }

    private void remember(final String segmentId, final SegmentLive live) {
        segments.remove(segmentId);
        segments.put(segmentId, live);
        if (segments.size() > MAX_SEGMENTS) {
            segments.remove(segments.keySet().iterator().next());
        }
    }
}
