package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/** A screen polls the job's state as often as it draws, and the log must not notice. */
class TranslationJobStateLogTest {

    private static final int POLLS = 600;

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger pipelineLogger;

    @BeforeEach
    void attachAppender() {
        pipelineLogger = (Logger) LoggerFactory.getLogger("ua.bookloom.pipeline");
        appender.start();
        pipelineLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        pipelineLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void state_sixHundredCalls_writesNoLine() {
        final TranslationJobImpl translation =
                job(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), replies("ONE."));
        appender.list.clear();

        IntStream.range(0, POLLS).forEach(poll -> translation.state());

        assertThat(appender.list).isEmpty();
    }
}
