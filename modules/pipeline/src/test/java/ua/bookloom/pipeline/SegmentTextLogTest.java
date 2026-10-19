package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ua.bookloom.api.pipeline.JobState;

/**
 * A log file written at the usual levels never holds the book: the text travels in the events only, and the log names
 * each segment by its id — on every line from the segment's start to its decision, through the MDC key
 * {@code segment}.
 */
class SegmentTextLogTest {

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger bookloom;
    private @Nullable Level previousLevel;

    @BeforeEach
    void attachAppender() {
        bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        previousLevel = bookloom.getLevel();
        bookloom.setLevel(Level.DEBUG);
        appender.start();
        bookloom.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        bookloom.detachAppender(appender);
        bookloom.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void run_doorSentenceAtDebug_logsItsIdAndNeverItsText() {
        final TranslationJobImpl translation = job(LiveTextFixtures.doorProject(tempDir), LiveTextFixtures.doorModel());

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(line -> line.contains(LiveTextFixtures.DOOR_ID));
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.DEBUG))
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(line -> line.contains("He opened") || line.contains("Він відчинив"));
    }

    @Test
    void run_fastThreeSegments_everyLineFromStartToDecisionCarriesTheSegmentId() {
        final TranslationJobImpl translation = job(
                TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree."),
                TranslationJobTestSupport.replies("Один.", "Два.", "Три."));

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(segmentIdsPerSpan(appender.list))
                .containsExactly(List.of("Book.txt:0"), List.of("Book.txt:1"), List.of("Book.txt:2"));
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    /**
     * The distinct MDC {@code segment} values of the lines of each span that opens with the line sending a
     * SegmentStarted event and closes with the line sending the SegmentDecided event after it, both included; a line
     * without the key reads as the empty string.
     */
    private static List<List<String>> segmentIdsPerSpan(final List<ILoggingEvent> lines) {
        final List<Integer> starts = indicesOf(lines, "type=SegmentStarted");
        final List<Integer> ends = indicesOf(lines, "type=SegmentDecided");
        assertThat(starts).hasSameSizeAs(ends);
        return IntStream.range(0, starts.size())
                .mapToObj(span -> lines.subList(starts.get(span), ends.get(span) + 1).stream()
                        .map(line -> line.getMDCPropertyMap().getOrDefault("segment", ""))
                        .distinct()
                        .toList())
                .toList();
    }

    private static List<Integer> indicesOf(final List<ILoggingEvent> lines, final String eventType) {
        return IntStream.range(0, lines.size())
                .filter(index ->
                        lines.get(index).getFormattedMessage().equals("Sending translation job event " + eventType))
                .boxed()
                .toList();
    }
}
