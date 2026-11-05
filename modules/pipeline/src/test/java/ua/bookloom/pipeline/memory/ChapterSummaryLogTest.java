package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.FILTER;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.FORMAT;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.SOURCE;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.call;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.chapter;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.map;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.reply;

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
import ua.bookloom.pipeline.ScriptedChatModel;

/** A chapter summary is book text: the log holds it at TRACE only, and its lifecycle line names counts alone. */
class ChapterSummaryLogTest {

    private static final String SUMMARY =
            "Оповідачка вирушає з села до перевалу, щоб забрати ключ. Глафіра лишається в селі.";
    private static final String TEXT_PART = "Оповідачка вирушає з села до перевалу";

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
    void summaryText_onlyAtTrace() {
        assertThat(summarize()).as("kept").isTrue();

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.INFO)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains("chapter summary chapter=1 sentences=2 dropped=0");
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.DEBUG))
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(line -> line.contains(TEXT_PART));

        appender.list.clear();
        bookloom.setLevel(Level.TRACE);
        summarize();

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.TRACE)
                .extracting(ILoggingEvent::getFormattedMessage)
                .filteredOn(line -> line.startsWith("Chapter summary kept"))
                .singleElement()
                .asString()
                .contains(TEXT_PART);
    }

    private static boolean summarize() {
        return call(new ScriptedChatModel().answerTo(FORMAT, reply(SUMMARY)), event -> {})
                .summarize(chapter(1, 2), SOURCE, map(List.of()), FILTER)
                .isPresent();
    }
}
