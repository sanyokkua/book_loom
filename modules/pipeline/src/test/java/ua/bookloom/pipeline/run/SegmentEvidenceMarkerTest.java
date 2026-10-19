package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.util.log.EvidenceLog;

/**
 * A flagged or repaired segment's decision tells the evidence log to keep the TRACE lines held for it; a segment
 * accepted at once does not, so the evidence file holds only what someone will ask about.
 */
class SegmentEvidenceMarkerTest {

    // The marker tests send no model call; a decision's note goes nowhere.
    private static final ModelCalls NO_CALLS = (kind, segmentId, request) -> {
        throw new AssertionError("no model call expected");
    };

    // Logback reads the MDC lazily; an appender that holds events must capture it while the span is still open.
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(final ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SegmentEvents.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    // IF a decision that needs explaining carried no marker, THEN the evidence log could not tell it from the rest.
    @ParameterizedTest
    @CsvSource({
        "FLAGGED, DRAFT, true",
        "FLAGGED, REPAIRED, true",
        "ACCEPTED, REPAIRED, true",
        "ACCEPTED, DRAFT, false",
        "ACCEPTED, TM_REUSE, false"
    })
    void decided_statusAndPath_marksOnlyFlaggedOrRepairedForKeeping(
            final SegmentStatus status, final SegmentPath path, final boolean marked) {
        final SegmentEvents events = new SegmentEvents(event -> {}, Map.of(), NO_CALLS);

        events.decided(record(status, path), null, new JobProgress(JobStage.TRANSLATE, 1, 1, 0, 0, 3));

        assertThat(appender.list.stream().anyMatch(SegmentEvidenceMarkerTest::isKeep))
                .isEqualTo(marked);
    }

    // The marker line must carry the segment key, or the evidence log would not know whose lines to write.
    @Test
    void decided_flaggedSegment_keepLineCarriesTheSegmentKey() {
        final SegmentEvents events = new SegmentEvents(event -> {}, Map.of(), NO_CALLS);

        events.decided(
                record(SegmentStatus.FLAGGED, SegmentPath.DRAFT),
                null,
                new JobProgress(JobStage.TRANSLATE, 1, 1, 0, 0, 3));

        assertThat(appender.list.stream()
                        .filter(SegmentEvidenceMarkerTest::isKeep)
                        .map(line -> line.getMDCPropertyMap().get(EvidenceLog.SEGMENT_KEY)))
                .containsExactly("Book.md:0");
    }

    private static boolean isKeep(final ILoggingEvent line) {
        return line.getMarkerList() != null
                && line.getMarkerList().stream().anyMatch(marker -> EvidenceLog.KEEP_MARKER.equals(marker.getName()));
    }

    private static SegmentRecord record(final SegmentStatus status, final SegmentPath path) {
        return new SegmentRecord(
                "s1",
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                status,
                null,
                null,
                null,
                null,
                0.0,
                null,
                List.of(),
                path,
                0,
                false,
                null);
    }
}
