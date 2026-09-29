package ua.bookloom.ui.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentStarted;

/**
 * Turns the run's events into tagged activity-log entries that name a segment by the locator a person reads.
 *
 * <p>The locator comes from the segment's own start announcement; the id is used only for a segment that was never
 * announced. Not thread-safe: {@link RunSession} calls it under its publish lock.
 */
@Slf4j
final class ActivityLogFeed {

    static final String STAGE_STARTED = "stageStarted";
    static final String PAUSED = "paused";
    static final String RESUMED = "resumed";
    static final String FINISHED = "finished";
    static final String RESUME_AFTER_ERROR = "resume";
    static final String REPAIR = "repair";
    static final String GLOSSARY = "glossary";
    static final String MEMORY = "memory";

    /** A chunk holds a handful of segments, so far fewer locators than this are ever looked up after their start. */
    private static final int MAX_LOCATORS = 256;

    private final Map<String, String> locators = new LinkedHashMap<>();
    private boolean pausedOnError;

    void segmentStarted(final SegmentStarted event) {
        locators.remove(event.segmentId());
        locators.put(event.segmentId(), event.locator());
        if (locators.size() > MAX_LOCATORS) {
            locators.remove(locators.keySet().iterator().next());
        }
    }

    String locatorOf(final String segmentId) {
        final String locator = locators.get(segmentId);
        if (locator == null) {
            log.debug("segment {} was never announced, its id stands for its locator", segmentId);
            return segmentId;
        }
        return locator;
    }

    Optional<LogEntry> decided(final SegmentDecided event) {
        final String locator = locatorOf(event.segmentId());
        return switch (event.status()) {
            case ACCEPTED -> Optional.of(new LogEntry(LogKind.ACCEPTED, List.of(locator)));
            case FLAGGED -> {
                log.warn("segment {} was flagged, reason {}", event.segmentId(), event.reason());
                yield Optional.of(new LogEntry(LogKind.SEGMENT_ERROR, List.of(locator)));
            }
            case PENDING, REVISED -> {
                log.trace("segment {} is not a decision the log reports", event.segmentId());
                yield Optional.empty();
            }
        };
    }

    Optional<LogEntry> modelCall(final ModelCallFinished event) {
        final String segmentId = event.segmentId();
        final @Nullable LogKind kind =
                switch (event.kind()) {
                    case DIRECTED_FIX, REFLECT, IMPROVE, POLISH -> LogKind.REPAIRED;
                    case STRUCTURAL_REPAIR, PLACEHOLDER_REPAIR -> LogKind.RETRIED;
                    case DRAFT, JUDGE, PRESCAN, SUMMARY, REVISION -> null;
                };
        if (kind == null || segmentId == null) {
            log.trace("a {} call is not an activity-log entry", event.kind());
            return Optional.empty();
        }
        final String locator = locatorOf(segmentId);
        return Optional.of(new LogEntry(kind, kind == LogKind.RETRIED ? List.of(REPAIR, locator) : List.of(locator)));
    }

    LogEntry memory(final MemoryUpdated event) {
        return switch (event.kind()) {
            case GLOSSARY -> new LogEntry(LogKind.GLOSSARY_APPLIED, List.of(GLOSSARY, event.label()));
            case TM -> new LogEntry(LogKind.GLOSSARY_APPLIED, List.of(MEMORY, event.label()));
            case SUMMARY -> new LogEntry(LogKind.SUMMARY_UPDATED, List.of(event.label()));
        };
    }

    LogEntry stageStarted() {
        return milestone(STAGE_STARTED);
    }

    LogEntry paused(final Paused event) {
        pausedOnError = event.reason() == PauseReason.ON_ERROR;
        return milestone(PAUSED);
    }

    LogEntry resumed() {
        final boolean afterError = pausedOnError;
        pausedOnError = false;
        return afterError ? new LogEntry(LogKind.RETRIED, List.of(RESUME_AFTER_ERROR, "")) : milestone(RESUMED);
    }

    LogEntry finished() {
        return milestone(FINISHED);
    }

    private static LogEntry milestone(final String token) {
        return new LogEntry(LogKind.MILESTONE, List.of(token));
    }
}
