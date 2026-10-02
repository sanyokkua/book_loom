package ua.bookloom.pipeline;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobListener;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;

/**
 * Counts what a soak run announced instead of keeping the events, so the count itself does not grow the heap it
 * measures: pauses by code, the pauses the stall watchdog caused, recovery announcements by status, resumes and
 * decisions; and it takes the heap samples at each quarter of the book.
 *
 * <p>Called on the job thread only.
 */
final class SoakEvents implements JobListener {

    private final HeapSamples heap;
    private final Map<ErrorCode, Integer> pauses = new EnumMap<>(ErrorCode.class);
    private final Map<RecoveryWaiting.Status, Integer> recoveries = new EnumMap<>(RecoveryWaiting.Status.class);
    private int stalls;
    private int resumes;
    private int decided;
    private int finished;
    private int events;
    private int longestOutage;

    SoakEvents(final HeapSamples heap) {
        this.heap = Objects.requireNonNull(heap, "heap");
    }

    @Override
    public void onEvent(final JobEvent event) {
        events++;
        switch (event) {
            case Paused paused -> paused(paused);
            case RecoveryWaiting waiting -> recovering(waiting);
            case Resumed resumed -> resumes++;
            case SegmentDecided segment -> decided(segment.progress());
            case Finished done -> finished++;
            default -> {
                // Counted in events only.
            }
        }
    }

    private void paused(final Paused paused) {
        final AppError error = paused.error();
        if (error == null) {
            return;
        }
        pauses.merge(error.code(), 1, Integer::sum);
        if ("Model call stalled".equals(error.title())) {
            stalls++;
        }
    }

    private void recovering(final RecoveryWaiting waiting) {
        recoveries.merge(waiting.status(), 1, Integer::sum);
        longestOutage = Math.max(longestOutage, waiting.attempt());
    }

    private void decided(final JobProgress progress) {
        decided++;
        final int done = progress.accepted() + progress.flagged();
        heap.atFraction(done, done + progress.pending());
    }

    Map<ErrorCode, Integer> pauses() {
        return Map.copyOf(pauses);
    }

    Map<RecoveryWaiting.Status, Integer> recoveries() {
        return Map.copyOf(recoveries);
    }

    int stalls() {
        return stalls;
    }

    /** The most wakes one outage was announced with. */
    int longestOutage() {
        return longestOutage;
    }

    int resumes() {
        return resumes;
    }

    int decided() {
        return decided;
    }

    int finished() {
        return finished;
    }

    int events() {
        return events;
    }

    @Override
    public String toString() {
        return "events=" + events + " decided=" + decided + " pauses=" + pauses + " stalls=" + stalls + " recoveries="
                + recoveries + " longestOutage=" + longestOutage + " resumes=" + resumes;
    }
}
