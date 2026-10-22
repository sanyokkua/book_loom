package ua.bookloom.ui.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.project.SegmentPath;

/**
 * The rows of the live card: every segment of the chunk being translated, in reading order, each with how far it has
 * got. The first segment of another chunk clears them, so a directed fix or a review call of one segment never
 * replaces the chunk; only a draft call adds a segment the run did not announce.
 *
 * <p>Not thread-safe: {@link LiveCallState} calls it under the run session's publish lock.
 */
@Slf4j
final class ChunkRows {

    /** A chunk holds a handful of segments; a long section's announcements are cut to this many. */
    static final int MAX_ROWS = 64;

    private final Map<String, LiveRow> rows = new LinkedHashMap<>();
    private @Nullable ChunkPosition chunk;
    private @Nullable List<LiveRow> shown;

    void started(final SegmentStarted event) {
        final ChunkPosition at = event.position();
        if (!sameChunk(at)) {
            log.debug(
                    "live rows: chunk {} of section {} starts, {} rows cleared", at.chunk(), at.section(), rows.size());
            rows.clear();
            chunk = at;
        }
        log.debug("live rows: segment {} queued", event.segmentId());
        rows.putIfAbsent(
                event.segmentId(),
                new LiveRow(
                        event.segmentId(),
                        event.locator(),
                        event.displaySource(),
                        null,
                        LiveRow.Stage.QUEUED,
                        null,
                        List.of(),
                        null));
        bound();
        shown = null;
    }

    private boolean sameChunk(final ChunkPosition at) {
        final ChunkPosition now = chunk;
        return now != null && now.section() == at.section() && now.chunk() == at.chunk();
    }

    void call(final CallSnapshot call) {
        final boolean waiting = call.state() == CallState.WAITING;
        for (final CallSegment segment : call.segments()) {
            final LiveRow known = rows.get(segment.id());
            if (known != null) {
                rows.put(segment.id(), known.inCall(call.callId(), waiting));
            } else if (call.kind() == CallKind.DRAFT) {
                rows.put(segment.id(), unannounced(segment).inCall(call.callId(), waiting));
            }
        }
        for (final SegmentOutcomeNote note : call.outcomes()) {
            final LiveRow known = rows.get(note.segmentId());
            if (note.kind() == SegmentOutcomeNote.Kind.FELL_BACK && known != null) {
                log.debug("live rows: segment {} fell back ({})", note.segmentId(), note.detail());
                rows.put(note.segmentId(), known.fellBack(firstCode(note.detail())));
            }
        }
        log.debug(
                "live rows: call {} {} ({} segments)",
                call.callId(),
                call.state(),
                call.segments().size());
        bound();
        shown = null;
    }

    private static LiveRow unannounced(final CallSegment segment) {
        return new LiveRow(
                segment.id(),
                segment.locator(),
                segment.displaySource(),
                null,
                LiveRow.Stage.QUEUED,
                null,
                List.of(),
                null);
    }

    // A detail may name several codes ("MISSING,TOKENS"); the first is the one the row's hover words.
    private static String firstCode(final String detail) {
        final String first = detail.split("[^A-Za-z_]", 2)[0];
        return first.isEmpty() ? "OTHER" : first.toUpperCase(Locale.ROOT);
    }

    void drafted(final SegmentDrafted event) {
        final LiveRow known = rows.get(event.segmentId());
        log.debug("live rows: segment {} drafted, shown {}", event.segmentId(), known != null);
        if (known != null) {
            rows.put(event.segmentId(), known.drafted(event.displayTarget()));
            shown = null;
        }
    }

    void decided(final SegmentDecided event) {
        final LiveRow known = rows.get(event.segmentId());
        final LiveRow.Stage stage = stageOf(event);
        log.debug("live rows: segment {} decided {} as {}", event.segmentId(), event.status(), stage);
        if (known == null || stage == null) {
            return;
        }
        final SegmentDetail detail = event.detail();
        rows.put(
                event.segmentId(),
                known.decided(
                        stage,
                        detail == null ? null : detail.displayTarget(),
                        detail == null || stage != LiveRow.Stage.FLAGGED ? List.of() : detail.findingKinds()));
        shown = null;
    }

    private static LiveRow.@Nullable Stage stageOf(final SegmentDecided event) {
        final SegmentDetail detail = event.detail();
        final SegmentPath path = detail == null ? null : detail.path();
        return switch (event.status()) {
            case ACCEPTED ->
                path == SegmentPath.VERBATIM || path == SegmentPath.SOURCE_KEPT
                        ? LiveRow.Stage.KEPT
                        : LiveRow.Stage.ACCEPTED;
            case FLAGGED -> LiveRow.Stage.FLAGGED;
            case PENDING, REVISED -> null;
        };
    }

    /**
     * The rows as they are now, one list shared until a row changes.
     *
     * @return the rows in reading order; never null
     */
    List<LiveRow> rows() {
        final List<LiveRow> now = shown;
        if (now != null) {
            return now;
        }
        final List<LiveRow> copied = List.copyOf(rows.values());
        shown = copied;
        return copied;
    }

    private void bound() {
        while (rows.size() > MAX_ROWS) {
            rows.remove(rows.keySet().iterator().next());
        }
    }
}
