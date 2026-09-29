package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.MONSTER_TARGET;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The log names each action, its segment and its outcome, and holds the person's text only at TRACE. */
class SegmentActionsLogTest {

    private static final String EDIT = "Чудовисько стріло мене опівночі.";

    @TempDir
    private Path tempDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger bookloom;
    private @Nullable Level previousLevel;

    @BeforeEach
    void attachAppender() {
        bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        previousLevel = bookloom.getLevel();
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
    void accept_flaggedSegment_logsTheActionAtInfoWithItsOutcome() {
        // INFO: action, segment id, resulting status, reviewed
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);

        desk.actions().accept(desk.projectId(), FLAGGED_ID);

        assertThat(lines(Level.INFO)).contains("accept segment=ch05.xhtml:11 status=ACCEPTED reviewed=true");
        assertThat(lines(Level.DEBUG))
                .anyMatch(line -> line.startsWith("accept: status=FLAGGED hasMachineTarget=true"));
    }

    @Test
    void accept_pendingSegment_logsTheRefusalAtWarnWithSegmentAndCode() {
        // WARN: a refused transition, with the segment id and the code
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = ReviewFixtures.epub(tempDir);

        desk.actions().accept(desk.projectId(), FLAGGED_ID);

        assertThat(lines(Level.WARN)).contains("accept refused segment=ch05.xhtml:11 code=validation");
    }

    @Test
    void saveEdit_atDebug_neverLogsTheEditedText() {
        // book text is written only when someone raises the level to TRACE
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);

        desk.actions().saveEdit(desk.projectId(), FLAGGED_ID, EDIT);

        assertThat(lines(Level.DEBUG)).noneMatch(line -> line.contains("Чудовисько"));
    }

    @Test
    void saveEdit_atTrace_logsTheEditedText() {
        // TRACE carries the value that explains the outcome
        bookloom.setLevel(Level.TRACE);
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, MONSTER_TARGET);

        desk.actions().saveEdit(desk.projectId(), FLAGGED_ID, EDIT);

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.TRACE)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains("saveEdit segment=ch05.xhtml:11 text=" + EDIT);
    }

    private List<String> lines(final Level atLeast) {
        return appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(atLeast) && event.getLevel() != Level.TRACE)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
