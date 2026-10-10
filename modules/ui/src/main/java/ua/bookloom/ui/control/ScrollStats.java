package ua.bookloom.ui.control;

import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Counts what the scroll code takes in and moves and writes one DEBUG line a second instead of one line per event: a
 * trackpad sends a couple of hundred events a second to the FX thread, where a log line per event is a cost of its own.
 * The per-event lines come back with {@code -Dbookloom.log.scroll=true}.
 */
@Slf4j
final class ScrollStats {

    /** The system property that brings back one TRACE line per scroll event. */
    static final String PER_EVENT_PROPERTY = "bookloom.log.scroll";

    private static final long REPORT_NANOS = 1_000_000_000L;
    private static final double NANOS_PER_MILLI = 1e6;

    private final LongSupplier nanos;
    private final boolean perEvent;
    private long windowStart;
    private long lastEvent;
    private long events;
    private long maxGap;
    private double pixelsIn;
    private double pixelsOut;
    private long detached;

    ScrollStats(final LongSupplier nanos, final boolean perEvent) {
        this.nanos = nanos;
        this.perEvent = perEvent;
    }

    /** Reads the property; the clock is the system's. */
    static ScrollStats system() {
        return new ScrollStats(System::nanoTime, Boolean.getBoolean(PER_EVENT_PROPERTY));
    }

    boolean isPerEvent() {
        return perEvent;
    }

    void eventIn(final double pixels) {
        final long now = nanos.getAsLong();
        if (events == 0 && windowStart == 0) {
            windowStart = now;
        }
        if (lastEvent != 0) {
            maxGap = Math.max(maxGap, now - lastEvent);
        }
        lastEvent = now;
        events++;
        pixelsIn += Math.abs(pixels);
        report(now);
    }

    void moved(final double pixels) {
        pixelsOut += Math.abs(pixels);
        report(nanos.getAsLong());
    }

    /** An event of a gesture whose target has left the scene, handed on by {@link GestureRescue}. */
    void targetDetached() {
        detached++;
        report(nanos.getAsLong());
    }

    long detachedInWindow() {
        return detached;
    }

    private void report(final long now) {
        if (now - windowStart < REPORT_NANOS) {
            return;
        }
        if (events > 0 || pixelsOut > 0 || detached > 0) {
            log.debug(
                    "scroll: {} events, {} px in, {} px out, max gap {} ms, {} target detached",
                    events,
                    Math.round(pixelsIn),
                    Math.round(pixelsOut),
                    Math.round(maxGap / NANOS_PER_MILLI),
                    detached);
        }
        windowStart = now;
        events = 0;
        maxGap = 0;
        pixelsIn = 0;
        pixelsOut = 0;
        detached = 0;
    }
}
