package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.pipeline.JobState;

/**
 * A log file written at the usual levels never holds the book: the text travels in the events only, and the log names
 * each segment by its id.
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
}
