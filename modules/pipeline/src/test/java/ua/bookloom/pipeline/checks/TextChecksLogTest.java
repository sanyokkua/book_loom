package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The text checks write the words they found at TRACE only: a DEBUG log never holds the book. */
class TextChecksLogTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger bookloom;
    private @Nullable Level previousLevel;

    @BeforeEach
    void attachAppender() {
        bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        previousLevel = bookloom.getLevel();
        bookloom.setLevel(Level.TRACE);
        appender.start();
        bookloom.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        bookloom.detachAppender(appender);
        bookloom.setLevel(previousLevel);
        appender.stop();
    }

    private List<String> atDebugOrAbove() {
        return appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.DEBUG))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    // IF a listed foreign word were logged at DEBUG, THEN the book's words would reach an ordinary log file.
    @Test
    void run_listedForeignWord_logsTheWordAtTraceOnly() {
        TextChecks.run("He came too.", "Він тоже прийшов.", "en", "uk");

        assertThat(atDebugOrAbove()).noneMatch(line -> line.contains("тоже") || line.contains("теж"));
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(line -> line.contains("тоже"));
    }

    @Test
    void run_glossaryNameLostOrSpelledAnotherWay_logsTheNameAtTraceOnly() {
        TextChecks.run("They reached Zurich at dawn.", "Вони дісталися міста.", "en", "uk", List.of("Zurich → Цюріх"));
        TextChecks.run("David stayed home.", "Тавид залишився вдома.", "en", "uk", List.of("David → Давид"));

        assertThat(atDebugOrAbove()).noneMatch(line -> line.contains("Zurich") || line.contains("David"));
    }
}
