package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;

/** The controls the dashboard offers in each state of a run, and what pressing them asks of the job. */
class TranslatingViewModelControlTest extends TranslatingViewModelTestBase {

    private static final Controls START_ONLY = new Controls(
            ControlState.ENABLED, ControlState.HIDDEN, ControlState.HIDDEN, ControlState.HIDDEN, ControlState.HIDDEN);
    private static final Controls PAUSE_AND_STOP = new Controls(
            ControlState.HIDDEN, ControlState.HIDDEN, ControlState.ENABLED, ControlState.HIDDEN, ControlState.ENABLED);
    private static final Controls PAUSING = new Controls(
            ControlState.HIDDEN, ControlState.HIDDEN, ControlState.DISABLED, ControlState.HIDDEN, ControlState.ENABLED);
    private static final Controls RESUME_AND_STOP = new Controls(
            ControlState.HIDDEN, ControlState.HIDDEN, ControlState.HIDDEN, ControlState.ENABLED, ControlState.ENABLED);
    private static final Controls STOPPING = new Controls(
            ControlState.HIDDEN, ControlState.HIDDEN, ControlState.HIDDEN, ControlState.HIDDEN, ControlState.DISABLED);
    private static final Controls NEW_RUN_ONLY = new Controls(
            ControlState.HIDDEN, ControlState.ENABLED, ControlState.HIDDEN, ControlState.HIDDEN, ControlState.HIDDEN);

    // IF the view model offered anything but start before a run, THEN a person could pause a run that does not exist.
    @Test
    void controls_beforeAnyRun_offerStartOnly() {
        buildViewModel();

        assertThat(controls()).isEqualTo(START_ONLY);
    }

    // IF start stayed pressable while the run is being prepared, THEN a double click would build two runs.
    @Test
    void controls_whilePreparing_startIsUnavailable() {
        openBookAndChooseModel();
        buildViewModel();

        press(viewModel::start);

        assertThat(controls().start()).isEqualTo(ControlState.DISABLED);
    }

    @Test
    void controls_runStarted_offerPauseAndStop() throws Exception {
        openBookAndChooseModel();
        buildViewModel();

        startAndPrepare();

        assertThat(controls()).isEqualTo(PAUSE_AND_STOP);
    }

    // IF a pending pause left the pause control live, THEN it could be pressed again and again.
    @Test
    void pause_running_readsAsPausingWithPauseUnavailableAndStopStillAvailable() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        press(viewModel::pause);

        awaitState(RunState.PAUSING);
        assertThat(job.calls()).contains("pause");
        assertThat(controls()).isEqualTo(PAUSING);
    }

    // IF a second press reached the job, THEN a pending pause would be requested twice.
    @Test
    void pause_pressedTwice_asksTheJobOnce() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        press(viewModel::pause);
        press(viewModel::pause);

        assertThat(job.calls()).containsOnlyOnce("pause");
    }

    // IF the engine's report did not end the pending state, THEN the person could never resume.
    @Test
    void pause_engineReportsPaused_offersResumeAndStop() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::pause);

        job.emit(new Paused(PauseReason.REQUESTED, null, progress(5, 0, 5)));
        deliverAndTick();

        awaitState(RunState.PAUSED);
        assertThat(controls()).isEqualTo(RESUME_AND_STOP);
    }

    @Test
    void resume_paused_asksTheJobAndReturnsToRunningWhenTheEngineResumes() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::pause);
        job.emit(new Paused(PauseReason.REQUESTED, null, progress(5, 0, 5)));
        deliverAndTick();
        awaitState(RunState.PAUSED);

        press(viewModel::resume);
        job.emit(new Resumed(progress(5, 0, 5)));
        deliverAndTick();

        assertThat(job.calls()).contains("resume");
        awaitState(RunState.RUNNING);
        assertThat(controls()).isEqualTo(PAUSE_AND_STOP);
    }

    // IF a pending stop left the stop control live, THEN a second stop would be sent while the first still waits.
    @Test
    void stop_running_readsAsStoppingWithNothingElseOffered() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        press(viewModel::stop);

        awaitState(RunState.STOPPING);
        assertThat(job.calls()).contains("cancel");
        assertThat(controls()).isEqualTo(STOPPING);
    }

    // IF a stopped run offered anything but a new run, THEN a person would be offered a resume that cannot work.
    @Test
    void stop_thenTheRunReturnsCancelled_offersANewRunAndNoResume() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);

        job.finish(Result.ok(cancelledReport()));

        awaitState(RunState.STOPPED);
        assertThat(controls()).isEqualTo(NEW_RUN_ONLY);
    }

    // IF a failed run offered no way on, THEN the dashboard would be a dead end until the application restarted.
    @Test
    void controls_runFails_offerANewRun() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(error()));

        awaitState(RunState.FAILED);
        assertThat(controls()).isEqualTo(NEW_RUN_ONLY);
    }

    // IF a control pressed with no run reached the runner's job, THEN an old job could be paused by mistake.
    @Test
    void pauseResumeStop_noRunActive_askNoJob() {
        buildViewModel();

        press(viewModel::pause);
        press(viewModel::resume);
        press(viewModel::stop);

        assertThat(job.calls()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
    }
}
