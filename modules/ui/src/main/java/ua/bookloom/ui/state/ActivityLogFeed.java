package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RoundStarted;
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
    static final String GLOSSARY = "glossary";
    static final String MEMORY = "memory";

    /** A chunk holds a handful of segments, so far fewer locators than this are ever looked up after their start. */
    private static final int MAX_LOCATORS = 256;

    private static final long SECONDS_PER_MINUTE = 60;

    private final Map<String, String> locators = new LinkedHashMap<>();
    // The round each segment in repair is in, so a call's line can say which round it belongs to.
    private final Map<String, Integer> rounds = new HashMap<>();
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
        rounds.remove(event.segmentId());
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

    /**
     * The locator a call is named by: its one segment, the first of several with how many more, or nothing.
     *
     * @param segmentIds the segments the call is about
     * @return the locator text, possibly empty
     */
    String callLocator(final List<String> segmentIds) {
        if (segmentIds.isEmpty()) {
            return "";
        }
        final String first = locatorOf(segmentIds.getFirst());
        return segmentIds.size() == 1 ? first : first + " +" + (segmentIds.size() - 1);
    }

    LogEntry modelCall(final ModelCallFinished event) {
        final String locator = callLocator(event.segmentIds());
        final String round =
                event.segmentId() == null ? "0" : String.valueOf(rounds.getOrDefault(event.segmentId(), 0));
        final List<String> args = List.of(
                WaitingCall.tokenOf(event.kind()),
                locator.isEmpty() ? "" : " · " + locator,
                round,
                String.valueOf(event.attempt()),
                clock(event.elapsed()));
        final ErrorCode failure = event.failure();
        if (failure == null) {
            return new LogEntry(LogKind.MODEL_CALL, args);
        }
        if (failure == ErrorCode.cancelled) {
            log.debug(
                    "a {} call for {} was interrupted by a pause or stop on attempt {}",
                    event.kind(),
                    locator,
                    event.attempt());
            return new LogEntry(LogKind.CALL_PAUSED, args);
        }
        log.debug("a {} call for {} failed with {} on attempt {}", event.kind(), locator, failure, event.attempt());
        return new LogEntry(
                LogKind.CALL_FAILED,
                List.of(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4), failure.name()));
    }

    LogEntry roundStarted(final RoundStarted event) {
        rounds.put(event.segmentId(), event.round());
        final String finding = event.blockingFinding();
        return new LogEntry(
                LogKind.ROUND,
                List.of(
                        locatorOf(event.segmentId()),
                        String.valueOf(event.round()),
                        String.valueOf(event.rounds()),
                        finding == null ? "none" : finding.toLowerCase(Locale.ROOT)));
    }

    private static String clock(final Duration elapsed) {
        final long seconds = elapsed.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
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
