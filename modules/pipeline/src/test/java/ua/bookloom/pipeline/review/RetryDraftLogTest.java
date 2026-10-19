package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.MONSTER_TARGET;
import static ua.bookloom.pipeline.review.ReviewFixtures.accept;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

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
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The retry and the desk log each outcome, refusal and delegation, and hold book text and the note only at TRACE. */
class RetryDraftLogTest {

    private static final String DESK_LOGGER = ReviewDeskImpl.class.getName();

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
    void retry_passing_logsTheOutcomeAtInfoAndTheReplayedContextAndRunStateAtDebug() {
        // INFO: segment id, resulting status, reviewed; DEBUG: the rebuilt context's counts and the run state read
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = flagged(JobState.PAUSED);

        desk.reviewDesk(ReviewMode.ASSISTED)
                .retry(desk.projectId(), FLAGGED_ID, RetryDraftTest.NOTE, true, RetryDraftTest.passing());

        assertThat(lines(Level.INFO)).contains("retry segment=ch05.xhtml:11 status=ACCEPTED reviewed=true");
        assertThat(lines(Level.DEBUG))
                .contains(
                        "retry project=" + desk.projectId()
                                + " segment=ch05.xhtml:11 hasNote=true lowerTemperature=true part=RetryDraft",
                        "retry segment=ch05.xhtml:11 latestRun=PAUSED",
                        "retry context segment=ch05.xhtml:11 preceding=1 terms=1 memoryHits=2 summary=true");
    }

    @Test
    void retry_runningRun_logsTheBusyRefusalAtWarn() {
        // WARN: a refused retry with its segment id and code
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = flagged(JobState.RUNNING);

        desk.reviewDesk(ReviewMode.ASSISTED).retry(desk.projectId(), FLAGGED_ID, null, false, RetryDraftTest.passing());

        assertThat(lines(Level.WARN)).contains("retry refused segment=ch05.xhtml:11 code=busy");
    }

    @Test
    void retry_failingOnAccepted_logsTheUnchangedRecordAtDebug() {
        // DEBUG: a failing retry on an ACCEPTED segment keeps the stored record
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = ReviewFixtures.epub(tempDir);
        latestRun(desk, JobState.COMPLETED);
        accept(desk, FLAGGED_ID, MONSTER_TARGET);
        withContext(desk, FLAGGED_ID, RetryDraftTest.SNAPSHOT);

        desk.reviewDesk(ReviewMode.ASSISTED)
                .retry(desk.projectId(), FLAGGED_ID, null, false, RetryDraftTest.answering(RetryDraftTest.TOO_SHORT));

        assertThat(lines(Level.DEBUG))
                .anyMatch(line -> line.startsWith("retry failed on an accepted segment=ch05.xhtml:11; stored record"));
        assertThat(lines(Level.INFO)).contains("retry segment=ch05.xhtml:11 status=ACCEPTED reviewed=false");
    }

    @Test
    void retry_atDebug_neverLogsTheNoteOrTheTexts() {
        // the note and the book's text are written only when someone raises the level to TRACE
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = flagged(JobState.PAUSED);

        desk.reviewDesk(ReviewMode.ASSISTED)
                .retry(desk.projectId(), FLAGGED_ID, RetryDraftTest.NOTE, true, RetryDraftTest.passing());

        assertThat(lines(Level.DEBUG)).noneMatch(line -> line.contains("Чудовисько") || line.contains("formal"));
    }

    @Test
    void retry_atTrace_logsTheNoteAndTheRetriedTarget() {
        // TRACE carries the retried texts
        bookloom.setLevel(Level.TRACE);
        final Desk desk = flagged(JobState.PAUSED);

        desk.reviewDesk(ReviewMode.ASSISTED)
                .retry(desk.projectId(), FLAGGED_ID, RetryDraftTest.NOTE, true, RetryDraftTest.passing());

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.TRACE)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains(
                        "retry segment=ch05.xhtml:11 note=" + RetryDraftTest.NOTE,
                        "retry segment=ch05.xhtml:11 target=" + MONSTER_TARGET);
    }

    @Test
    void readsAndCounts_logNothingFromTheDesk() {
        // queue, segment and counts are pure reads: the desk writes no line for them
        bookloom.setLevel(Level.TRACE);
        final Desk desk = flagged(JobState.PAUSED);
        final ReviewDeskImpl port = desk.reviewDesk(ReviewMode.ASSISTED);

        port.queue(desk.projectId(), ReviewFilter.ALL_FLAGGED);
        port.segment(desk.projectId(), FLAGGED_ID);
        port.counts(desk.projectId());

        assertThat(appender.list).noneMatch(event -> event.getLoggerName().equals(DESK_LOGGER));
    }

    @Test
    void accept_logsTheDelegationAtDebug() {
        // DEBUG: each port method's entry, its parameters and the part it delegates to
        bookloom.setLevel(Level.DEBUG);
        final Desk desk = flagged(JobState.PAUSED);

        desk.reviewDesk(ReviewMode.ASSISTED).accept(desk.projectId(), FLAGGED_ID);

        assertThat(lines(Level.DEBUG))
                .contains("accept project=" + desk.projectId() + " segment=ch05.xhtml:11 part=SegmentActions");
    }

    private Desk flagged(final JobState run) {
        final Desk desk = ReviewFixtures.epub(tempDir);
        latestRun(desk, run);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        withContext(desk, FLAGGED_ID, RetryDraftTest.SNAPSHOT);
        return desk;
    }

    private List<String> lines(final Level atLeast) {
        return appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(atLeast) && event.getLevel() != Level.TRACE)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
