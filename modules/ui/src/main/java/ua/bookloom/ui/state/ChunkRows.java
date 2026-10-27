package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * The rows of the live card: every segment the run has started, in book order, each with how far it has got. The
 * chunk being translated is last and earlier chunks stay above it as history, so a reviewed chunk whose check decides
 * all its drafts at once never empties the card. A directed fix or a review call of one segment never adds a
 * row; only a draft call adds a segment the run did not announce. The history is the run's own: a new run session
 * starts with an empty one.
 *
 * <p>Not thread-safe: {@link LiveCallState} calls it under the run session's publish lock.
 */
@Slf4j
final class ChunkRows {

    /** How many rows the card keeps; the oldest goes first, so a whole book is never held or drawn at once. */
    static final int MAX_ROWS = 500;

    private final Map<String, LiveRow> rows = new HashMap<>();
    // The rows in book order. The run announces segments in book order, but a batch call goes out with segments the
    // run has not announced yet; those wait in place, and a segment announced later goes before the first of them.
    private final List<String> order = new ArrayList<>();
    private final Set<String> unannounced = new HashSet<>();
    private @Nullable List<LiveRow> shown;

    void started(final SegmentStarted event) {
        final ChunkPosition at = event.position();
        final LiveRow known = rows.get(event.segmentId());
        if (known != null && !known.stage().isDecided()) {
            log.debug("live rows: segment {} started again in chunk {}, kept in place", event.segmentId(), at.chunk());
            unannounced.remove(event.segmentId());
            return;
        }
        if (known != null) {
            log.debug("live rows: decided segment {} started again, moved to the end", event.segmentId());
            remove(event.segmentId());
        }
        log.debug(
                "live rows: segment {} queued in chunk {} of section {}", event.segmentId(), at.chunk(), at.section());
        rows.put(
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
        order.add(firstUnannounced(), event.segmentId());
        bound();
        shown = null;
    }

    // Where an announced segment goes: before the first row a call listed ahead of its announcement, else at the end.
    private int firstUnannounced() {
        for (int index = 0; index < order.size(); index++) {
            if (unannounced.contains(order.get(index))) {
                log.debug("live rows: placed before {}, which a call listed early", order.get(index));
                return index;
            }
        }
        return order.size();
    }

    private void remove(final String segmentId) {
        rows.remove(segmentId);
        order.remove(segmentId);
        unannounced.remove(segmentId);
    }

    void call(final CallSnapshot call) {
        final boolean waiting = call.state() == CallState.WAITING;
        for (final CallSegment segment : call.segments()) {
            final LiveRow known = rows.get(segment.id());
            if (known != null) {
                rows.put(segment.id(), known.inCall(call.callId(), waiting));
            } else if (call.kind() == CallKind.DRAFT) {
                rows.put(segment.id(), unannounced(segment).inCall(call.callId(), waiting));
                order.add(segment.id());
                unannounced.add(segment.id());
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
        final List<LiveRow> copied = order.stream().map(rows::get).toList();
        shown = copied;
        return copied;
    }

    private void bound() {
        final int over = order.size() - MAX_ROWS;
        if (over > 0) {
            log.debug("live rows: {} oldest rows dropped to keep {}", over, MAX_ROWS);
        }
        while (order.size() > MAX_ROWS) {
            remove(order.getFirst());
        }
    }
}
