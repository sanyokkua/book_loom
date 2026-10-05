package ua.bookloom.app.bootstrap;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import ua.bookloom.util.log.EvidenceLog;
import ua.bookloom.util.paths.AppPaths;

/**
 * The evidence log's appender, built by {@link LoggingBootstrap} beside the detailed log: it keeps the full TRACE of
 * every flagged or repaired segment for the whole run, outside the detailed log's rotation. Like that class it is part
 * of the one Logback carve-out of {@code logging.md}: only the bootstrap's own classes touch Logback.
 *
 * <p>It holds each segment's BookLoom lines back until the pipeline says the segment is worth keeping, then writes them
 * and everything after them to the evidence file.
 *
 * <p>Memory is bounded by the number of segments held and the lines held per segment; the oldest segment that was
 * never kept is forgotten first. Lines that name no segment (a reviewer call reads a whole chunk) are held in one
 * short queue and written ahead of the next segment kept, once. A segment arrives in two spans (its draft, then
 * its decision) with its chunk-mates in between, which is why lines are held per segment key and not in order.
 */
final class EvidenceAppender extends AppenderBase<ILoggingEvent> {

    private static final String BOOKLOOM_LOGGERS = "ua.bookloom";
    // 10 MB active plus three gzip archives.
    private static final String EVIDENCE_MAX_FILE_SIZE = "10MB";
    private static final int EVIDENCE_ARCHIVES = 3;
    private static final String EVIDENCE_ARCHIVE_PATTERN = AppPaths.TRACE_LOG_PREFIX + "-evidence.%i.log.gz";
    // Segments held back at once and lines held per segment; a drafting chunk is at most eight segments.
    private static final int EVIDENCE_HELD_SEGMENTS = 64;
    private static final int EVIDENCE_LINES_PER_SEGMENT = 400;

    /**
     * Builds the evidence log: a rolling file of its own behind this appender, so that what explains a flagged or
     * repaired segment outlives the detailed log's rotation.
     *
     * @param context the logger context the appenders belong to
     * @param logDir the resolved log directory
     * @param encoder the detailed log's encoder, so the file starts with the same session header
     * @return the started appender to attach to the root logger
     */
    static Appender<ILoggingEvent> create(
            LoggerContext context, Path logDir, LayoutWrappingEncoder<ILoggingEvent> encoder) {
        final RollingFileAppender<ILoggingEvent> file = new RollingFileAppender<>();
        file.setContext(context);
        file.setName("EVIDENCE_FILE");
        file.setFile(logDir.resolve(AppPaths.EVIDENCE_LOG_FILE_NAME).toString());
        file.setEncoder(encoder);

        final FixedWindowRollingPolicy rolling = new FixedWindowRollingPolicy();
        rolling.setContext(context);
        rolling.setParent(file);
        rolling.setFileNamePattern(logDir.resolve(EVIDENCE_ARCHIVE_PATTERN).toString());
        rolling.setMinIndex(1);
        rolling.setMaxIndex(EVIDENCE_ARCHIVES);
        rolling.start();

        final SizeBasedTriggeringPolicy<ILoggingEvent> trigger = new SizeBasedTriggeringPolicy<>();
        trigger.setContext(context);
        trigger.setMaxFileSize(FileSize.valueOf(EVIDENCE_MAX_FILE_SIZE));
        trigger.start();

        file.setRollingPolicy(rolling);
        file.setTriggeringPolicy(trigger);
        file.start();

        final EvidenceAppender evidence = new EvidenceAppender(file);
        evidence.setContext(context);
        evidence.setName("EVIDENCE");
        evidence.start();
        return evidence;
    }

    private final Appender<ILoggingEvent> file;
    private final Map<String, Deque<ILoggingEvent>> held = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Boolean> kept = new LinkedHashMap<>(16, 0.75f, true);
    private final Deque<ILoggingEvent> chunkLines = new ArrayDeque<>();

    EvidenceAppender(Appender<ILoggingEvent> file) {
        this.file = file;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isBookloom(event.getLoggerName())) {
            return;
        }
        event.prepareForDeferredProcessing();
        final String segment = event.getMDCPropertyMap().get(EvidenceLog.SEGMENT_KEY);
        if (segment == null) {
            hold(chunkLines, event);
        } else if (isKeep(event)) {
            keep(segment, event);
        } else if (kept.containsKey(segment)) {
            file.doAppend(event);
        } else {
            hold(held.computeIfAbsent(segment, key -> new ArrayDeque<>()), event);
            forgetOldest();
        }
    }

    private void keep(String segment, ILoggingEvent marked) {
        chunkLines.forEach(file::doAppend);
        chunkLines.clear();
        final Deque<ILoggingEvent> lines = held.remove(segment);
        if (lines != null) {
            lines.forEach(file::doAppend);
        }
        file.doAppend(marked);
        kept.put(segment, Boolean.TRUE);
        while (kept.size() > EVIDENCE_HELD_SEGMENTS) {
            kept.remove(kept.keySet().iterator().next());
        }
    }

    private void forgetOldest() {
        while (held.size() > EVIDENCE_HELD_SEGMENTS) {
            held.remove(held.keySet().iterator().next());
        }
    }

    private static void hold(Deque<ILoggingEvent> lines, ILoggingEvent event) {
        lines.addLast(event);
        if (lines.size() > EVIDENCE_LINES_PER_SEGMENT) {
            lines.removeFirst();
        }
    }

    private static boolean isKeep(ILoggingEvent event) {
        return event.getMarkerList() != null
                && event.getMarkerList().stream().anyMatch(marker -> EvidenceLog.KEEP_MARKER.equals(marker.getName()));
    }

    private static boolean isBookloom(String name) {
        return name.equals(BOOKLOOM_LOGGERS) || name.startsWith(BOOKLOOM_LOGGERS + ".");
    }

    @Override
    public void stop() {
        file.stop();
        super.stop();
    }
}
