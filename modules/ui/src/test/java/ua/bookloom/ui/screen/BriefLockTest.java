package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.state.BriefLock;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.WaitingCall;

/**
 * The two facts the brief reads from the run: it is read-only while the run is at work (translating, pausing, stopping,
 * a stuck call, waiting for the AI program), and its languages are locked once any segment is decided or the run is at
 * work, because the project's stored translations are of that pair.
 */
class BriefLockTest extends BriefRunScreenTestBase {

    private BriefLock lock() {
        return read(() -> injector.getInstance(BriefLock.class));
    }

    private boolean readOnly() {
        return read(() -> lock().readOnly().get());
    }

    private boolean languagesLocked() {
        return read(() -> lock().languagesLocked().get());
    }

    // The decided count comes from the run state source, the one place the figure is worked out.
    @Test
    void decidedSegments_pausedWith74Of100_isSeventyFour() throws Exception {
        openAndShowBrief();

        publishRun(RunState.PAUSED, 74);

        assertThat(read(() -> injector.getInstance(RunStateSource.class)
                        .decidedSegments()
                        .get()))
                .isEqualTo(74);
    }

    @ParameterizedTest(name = "{0} with {1} decided")
    @CsvSource({
        "RUNNING, 0, true, true",
        "RUNNING, 10, true, true",
        "PAUSING, 0, true, true",
        "STOPPING, 0, true, true",
        "PAUSED, 74, false, true",
        "PAUSED, 0, false, false",
        "STOPPED, 5, false, true",
        "COMPLETED, 100, false, true"
    })
    void lock_runState_readOnlyAndLanguagesLockedAsTheSpecSays(
            final RunState state, final int decided, final boolean readOnly, final boolean locked) throws Exception {
        openAndShowBrief();

        publishRun(state, decided);

        assertThat(readOnly()).isEqualTo(readOnly);
        assertThat(languagesLocked()).isEqualTo(locked);
    }

    @Test
    void lock_stuckCall_isReadOnlyAndLanguagesLocked() throws Exception {
        openAndShowBrief();
        publishRun(RunState.RUNNING, 0);

        mirror().live()
                .publishWaitingCall(new WaitingCall(
                        CallKind.DRAFT,
                        "ch2 · p05",
                        3,
                        1,
                        2,
                        Duration.ofSeconds(90),
                        Duration.ofMinutes(6),
                        Duration.ofSeconds(90),
                        true));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(readOnly()).isTrue();
        assertThat(languagesLocked()).isTrue();
    }

    // The run retries by itself while it waits for the AI program, so the brief is as read-only as while translating.
    @Test
    void readOnly_waitingForTheAiProgram_isTrue() throws Exception {
        openAndShowBrief();
        publishRun(RunState.PAUSED, 20);
        final AppError error = AppError.of(ErrorCode.unreachable, "Unreachable", "No answer.");

        mirror().review().publishProviderError(error);
        mirror().review()
                .publishRecovery(new RecoveryState(
                        RecoveryWaiting.Status.WAITING,
                        1,
                        LocalTime.of(2, 14),
                        Instant.parse("2026-10-02T03:00:00Z"),
                        error.code(),
                        30));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(readOnly()).isTrue();
    }

    // IF a brand-new project locked its languages, THEN a person could never choose the pair they came for.
    @Test
    void languagesLocked_newProjectNoDecisionNoRun_isFalse() throws Exception {
        openAndShowBrief();

        assertThat(languagesLocked()).isFalse();
        assertThat(readOnly()).isFalse();
    }

    // A reopened project with decided segments and no run in this session is locked: its translations are of that pair.
    @Test
    void languagesLocked_reopenedProjectWith74DecidedAndNoRun_isTrue() throws Exception {
        final ProjectSummary reopened =
                new ProjectSummary(stored("uk", NameOrigin.AUTO, AUTOMATIC_NAME).project(), 74, 100, null, null);
        projects.willList(List.of(reopened));

        readyToStart();
        showBrief();

        assertThat(languagesLocked()).isTrue();
        assertThat(readOnly()).isFalse();
    }
}
