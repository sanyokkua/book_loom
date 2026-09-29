package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.MemoryKind;
import ua.bookloom.api.pipeline.MemoryUpdated;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.project.SegmentPath;

/** Every event the log reports becomes one tagged entry that names its segment by the locator a person reads. */
class RunSessionLogFeedTest extends LiveSessionTestBase {

    private static final String LOCATOR = "ch7 · p42";

    // IF the entry named the segment by its id, THEN a log line would mean nothing to someone who reads chapters.
    @ParameterizedTest
    @EnumSource(
            value = CallKind.class,
            names = {"DIRECTED_FIX", "REFLECT", "IMPROVE", "POLISH"})
    void repairCall_logsFixNamingTheLocatorAndNotTheId(final CallKind kind) {
        final RunSession session = session();
        session.onEvent(started("ch07.xhtml:41", LOCATOR, "He opened the old door."));

        session.onEvent(call("ch07.xhtml:41", kind));
        tick(session);

        assertThat(logEntries()).containsExactly(log(LogKind.REPAIRED, LOCATOR));
    }

    // IF a format repair were not a retry, THEN the log would never show the model being asked twice.
    @ParameterizedTest
    @EnumSource(
            value = CallKind.class,
            names = {"STRUCTURAL_REPAIR", "PLACEHOLDER_REPAIR"})
    void formatRepairCall_logsRetryNamingTheLocator(final CallKind kind) {
        final RunSession session = session();
        session.onEvent(started("s-43", "ch7 · p43", "Text."));

        session.onEvent(call("s-43", kind));
        tick(session);

        assertThat(logEntries()).containsExactly(log(LogKind.RETRIED, "repair", "ch7 · p43"));
    }

    // IF a draft, judge or summary call logged, THEN the log would hold one line per call, not per decision.
    @ParameterizedTest
    @EnumSource(
            value = CallKind.class,
            names = {"DRAFT", "JUDGE", "PRESCAN", "SUMMARY", "REVISION"})
    void otherCall_logsNothing(final CallKind kind) {
        final RunSession session = session();

        session.onEvent(call("s-1", kind));
        tick(session);

        assertThat(logEntries()).isEmpty();
    }

    // IF a repair of a call with no segment logged, THEN it would name nothing.
    @Test
    void repairCall_withoutASegment_logsNothing() {
        final RunSession session = session();

        session.onEvent(new ModelCallFinished(null, CallKind.POLISH, Duration.ofSeconds(1), null, 10, false));
        tick(session);

        assertThat(logEntries()).isEmpty();
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
