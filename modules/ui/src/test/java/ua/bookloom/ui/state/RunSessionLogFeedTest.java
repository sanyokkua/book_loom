package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.api.project.SegmentPath;

/** Every event the log reports becomes one tagged entry that names its segment by the locator a person reads. */
class RunSessionLogFeedTest extends LiveSessionTestBase {

    private static final String LOCATOR = "ch7 · p42";

    // IF a call's line named the segment by its id, THEN it would mean nothing to someone who reads chapters.
    @ParameterizedTest
    @EnumSource(CallKind.class)
    void answeredCall_logsItsKindLocatorAttemptAndDuration(final CallKind kind) {
        final RunSession session = session();
        session.onEvent(started("ch07.xhtml:41", LOCATOR, "He opened the old door."));

        session.onEvent(call("ch07.xhtml:41", kind));
        tick(session);

        assertThat(logEntries())
                .containsExactly(log(LogKind.MODEL_CALL, WaitingCall.tokenOf(kind), " · " + LOCATOR, "0", "1", "0:01"));
    }

    // IF a failed attempt were not logged, THEN a stall would leave no trace but the clock.
    @Test
    void failedAttempt_logsTheFailureWithItsCode() {
        final RunSession session = session();
        session.onEvent(started("s-1", LOCATOR, "Text."));

        session.onEvent(new ModelCallFinished(
                "s-1", CallKind.JUDGE, Duration.ofSeconds(90), null, 0, false, List.of("s-1"), 2, ErrorCode.timeout));
        tick(session);

        assertThat(logEntries())
                .containsExactly(log(LogKind.CALL_FAILED, "judge", " · " + LOCATOR, "0", "2", "1:30", "timeout"));
    }

    // IF a chunk's judge call named only its first segment, THEN the line would hide that it judged several.
    @Test
    void chunkJudgeCall_namesItsFirstSegmentAndHowManyMore() {
        final RunSession session = session();
        session.onEvent(started("s-1", "ch7 · p41", "A."));
        session.onEvent(started("s-2", LOCATOR, "B."));

        session.onEvent(new ModelCallFinished(
                null, CallKind.JUDGE, Duration.ofSeconds(6), null, 40, false, List.of("s-1", "s-2"), 1, null));
        tick(session);

        assertThat(logEntries()).containsExactly(log(LogKind.MODEL_CALL, "judge", " · ch7 · p41 +1", "0", "1", "0:06"));
    }

    // IF a call in a repair round did not name its round, THEN three fixes of one segment would read as one.
    @Test
    void callInARound_namesTheRound() {
        final RunSession session = session();
        session.onEvent(started("s-1", LOCATOR, "Text."));
        session.onEvent(new RoundStarted("s-1", 2, 3, 0.85, "meaning"));

        session.onEvent(call("s-1", CallKind.DIRECTED_FIX));
        tick(session);

        assertThat(logEntries())
                .containsExactly(
                        log(LogKind.ROUND, LOCATOR, "2", "3", "meaning"),
                        log(LogKind.MODEL_CALL, "directed_fix", " · " + LOCATOR, "2", "1", "0:01"));
    }

    // IF a summary call named a segment, THEN the line would point at text the call never read.
    @Test
    void callOfNoSegment_hasNoLocator() {
        final RunSession session = session();

        session.onEvent(new ModelCallFinished(null, CallKind.SUMMARY, Duration.ofSeconds(1), null, 10, false));
        tick(session);

        assertThat(logEntries()).containsExactly(log(LogKind.MODEL_CALL, "summary", "", "0", "1", "0:01"));
    }

    // IF the same failure repeated as four lines, THEN a stall would push every other line out of view.
    @Test
    void sameFailureFourTimes_isOneLineCountedFour() {
        final RunSession session = session();
        final ModelCallFinished timedOut = new ModelCallFinished(
                null, CallKind.JUDGE, Duration.ofSeconds(90), null, 0, false, List.of(), 1, ErrorCode.timeout);

        session.onEvent(timedOut);
        session.onEvent(timedOut);
        tick(session);
        session.onEvent(timedOut);
        session.onEvent(timedOut);
        tick(session);

        assertThat(shownLogEntries())
                .singleElement()
                .extracting(LogEntry::repeats)
                .isEqualTo(4);
    }

    // IF a line carried no time, THEN a person could not tell how long a stall lasted.
    @Test
    void line_isStampedWithTheClocksTime() {
        final RunSession session = session();
        clock.advance(Duration.ofHours(9).plusMinutes(5).plusSeconds(7));

        session.onEvent(new MemoryUpdated(MemoryKind.SUMMARY, "3"));
        tick(session);

        assertThat(shownLogEntries()).singleElement().extracting(LogEntry::time).isEqualTo(LocalTime.of(9, 5, 7));
    }

    // IF each memory kind did not log with its own tag and subject, THEN the log would not show what was remembered.
    @Test
    void memoryUpdates_logMemAndSumWithTheirLabels() {
        final RunSession session = session();

        session.onEvent(new MemoryUpdated(MemoryKind.GLOSSARY, "+2"));
        session.onEvent(new MemoryUpdated(MemoryKind.TM, LOCATOR));
        session.onEvent(new MemoryUpdated(MemoryKind.SUMMARY, "3"));
        tick(session);

        assertThat(logEntries())
                .containsExactly(
                        log(LogKind.GLOSSARY_APPLIED, "glossary", "+2"),
                        log(LogKind.GLOSSARY_APPLIED, "memory", LOCATOR),
                        log(LogKind.SUMMARY_UPDATED, "3"));
    }

    // IF an accepted or flagged segment named its id, THEN the two most common entries would be unreadable.
    @Test
    void decisions_logOkAndErrNamingTheLocator() {
        final RunSession session = session();
        session.onEvent(started("s-1", "ch7 · p41", "A."));
        session.onEvent(started("s-2", LOCATOR, "B."));

        session.onEvent(decidedWith("s-1", SegmentStatus.ACCEPTED, detail(null, SegmentPath.DRAFT)));
        session.onEvent(decidedWith("s-2", SegmentStatus.FLAGGED, detail(0.2, SegmentPath.REPAIRED)));
        tick(session);

        assertThat(logEntries())
                .containsExactly(log(LogKind.ACCEPTED, "ch7 · p41"), log(LogKind.SEGMENT_ERROR, LOCATOR));
    }

    // IF the resume after a provider error were only a milestone, THEN the log would hide that the model was retried.
    @Test
    void pausedOnError_thenResumed_logsInfoThenRetry() {
        final RunSession session = session();

        session.onEvent(new Paused(PauseReason.ON_ERROR, error(), progress(1, 0, 1), "s-1"));
        session.onEvent(new Resumed(progress(1, 0, 1)));
        tick(session);

        assertThat(logEntries()).containsExactly(log(LogKind.MILESTONE, "paused"), log(LogKind.RETRIED, "resume", ""));
    }

    // IF a resume after a flagged pause were a retry, THEN the log would claim an error that never happened.
    @Test
    void pausedOnFlagged_thenResumed_logsTwoInfoEntries() {
        final RunSession session = session();

        session.onEvent(new Paused(PauseReason.ON_FLAGGED, null, progress(1, 0, 1), "s-1"));
        session.onEvent(new Resumed(progress(1, 0, 1)));
        tick(session);

        assertThat(logEntries()).containsExactly(log(LogKind.MILESTONE, "paused"), log(LogKind.MILESTONE, "resumed"));
    }

    // IF the pause on error left its mark after the resume, THEN a later ordinary resume would read as a retry.
    @Test
    void resumed_afterAnErrorPauseWasAlreadyResumed_logsInfo() {
        final RunSession session = session();
        session.onEvent(new Paused(PauseReason.ON_ERROR, error(), progress(1, 0, 1), null));
        session.onEvent(new Resumed(progress(1, 0, 1)));
        session.onEvent(new Paused(PauseReason.REQUESTED, null, progress(1, 0, 1)));

        session.onEvent(new Resumed(progress(1, 0, 1)));
        tick(session);

        assertThat(logEntries().get(3)).isEqualTo(log(LogKind.MILESTONE, "resumed"));
    }
}
