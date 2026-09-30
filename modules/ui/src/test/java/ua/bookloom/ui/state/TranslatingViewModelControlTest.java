package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedTranslationEngine;

/** The controls the dashboard offers in each state of a run, and what pressing them asks of the job. */
class TranslatingViewModelControlTest extends TranslatingViewModelTestBase {

    private static final ControlState HIDDEN = ControlState.HIDDEN;
    private static final ControlState ENABLED = ControlState.ENABLED;
    private static final ControlState DISABLED = ControlState.DISABLED;
    private static final Controls START_ONLY = new Controls(ENABLED, HIDDEN, HIDDEN, HIDDEN);
    private static final Controls PAUSE_AND_STOP = new Controls(HIDDEN, ENABLED, HIDDEN, ENABLED);
    private static final Controls PAUSING = new Controls(HIDDEN, DISABLED, HIDDEN, ENABLED);
    private static final Controls RESUME_AND_STOP = new Controls(HIDDEN, HIDDEN, ENABLED, ENABLED);
    private static final Controls STOPPING = new Controls(HIDDEN, HIDDEN, HIDDEN, DISABLED);
    private static final Controls RESUME_ONLY = new Controls(HIDDEN, HIDDEN, ENABLED, HIDDEN);

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

    // IF a stopped run offered a new run instead of a resume, THEN the session's work would be thrown away.
    @Test
    void stop_thenTheRunReturnsCancelled_offersResumeAndNoNewRun() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);

        job.finish(Result.ok(cancelledReport()));

        awaitState(RunState.STOPPED);
        assertThat(controls()).isEqualTo(RESUME_ONLY);
    }

    // IF a resume from a pause built another job, THEN the run would translate its segments twice.
    @Test
    void resume_paused_asksForNoNewJob() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::pause);
        job.emit(new Paused(PauseReason.REQUESTED, null, progress(5, 0, 5)));
        deliverAndTick();
        awaitState(RunState.PAUSED);

        press(viewModel::resume);

        assertThat(engine.requests()).hasSize(1);
        assertThat(queued.pending()).isZero();
    }

    // IF a resume after a stop reused the stopped job or imported the book again, THEN the work done so far would be
    // repeated or thrown away.
    @Test
    void resume_afterAStop_asksForASecondJobOnTheSameProjectAndImportsNothing() throws Exception {
        final RecordingJob resumed = new RecordingJob();
        engine = ScriptedTranslationEngine.returning(job, resumed);
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        press(viewModel::resume);
        queued.runAll();
        resumed.awaitRunStarted();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(engine.requests())
                .containsExactly(
                        new RunRequest("p1", ReviewMode.UNATTENDED), new RunRequest("p1", ReviewMode.UNATTENDED));
        assertThat(projects.imports()).containsExactly(BOOK);
        assertThat(state()).isEqualTo(RunState.RUNNING);
        assertThat(controls()).isEqualTo(PAUSE_AND_STOP);
    }

    // IF a start with no book open asked for a job, THEN a run would be built on nothing; the notice names the book.
    @Test
    void start_noBookOpen_isRefusedNamingTheBookAndAsksForNoJob() {
        chooseModel(MODEL);
        buildViewModel();

        press(viewModel::start);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.BOOK));
        assertThat(engine.requests()).isEmpty();
    }

    // IF a start with no model asked for a job, THEN the factory would be asked about a blank model.
    @Test
    void start_noModelChosen_isRefusedNamingTheModelAndAsksForNoJob() {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        buildViewModel();
        press(() -> imports.open(BOOK));
        press(() -> brief.setTargetLanguage("uk"));

        press(viewModel::start);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.MODEL));
        assertThat(engine.requests()).isEmpty();
    }

    // IF a failed run offered no way on, THEN the dashboard would be a dead end until the application restarted.
    @Test
    void controls_runFails_offerStart() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(error()));

        awaitState(RunState.FAILED);
        assertThat(controls()).isEqualTo(START_ONLY);
    }

    // IF start stayed pressable while the run is paused on a provider error, THEN a second run would begin beside the
    // one that can be resumed.
    @Test
    void start_pausedOnAProviderError_asksTheEngineForNoJob() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        final int requests = engine.requests().size();
        onFx(() -> {
            mirror.publishRunState(RunState.PAUSED);
            mirror.review().publishProviderError(failureOf(ErrorCode.unreachable));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        press(viewModel::start);

        assertThat(controls().start()).isEqualTo(ControlState.HIDDEN);
        assertThat(engine.requests()).hasSize(requests);
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
