package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedTranslationEngine;

/** Starting a run from the dashboard: what is prepared, where, and what happens when it cannot be. */
class TranslatingViewModelStartTest extends TranslatingViewModelTestBase {

    // IF start built the model or the job on the FX thread, THEN the window would freeze while a model loads.
    @Test
    void start_ready_holdsAllWorkBackUntilTheBackgroundExecutorRunsIt() {
        openBookAndChooseModel();
        buildViewModel();

        press(viewModel::start);

        assertThat(queued.pending()).isEqualTo(1);
        assertThat(models.selections()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertThat(preparing()).isTrue();
    }

    // IF the run were assembled from anything but the brief's request and the chosen model, THEN the run would
    // translate something other than what the person set up.
    @Test
    void start_ready_buildsTheModelAndJobOffTheFxThreadAndBeginsTheRun() throws Exception {
        openBookAndChooseModel();
        buildViewModel();

        startAndPrepare();

        assertThat(models.selections()).containsExactly(new ModelSelection("ollama", MODEL));
        assertThat(models.askedOnFxThread()).containsExactly(false);
        assertThat(engine.requests())
                .containsExactly(new TranslationRequest(BOOK, Path.of("Frankenstein.uk.epub"), "uk", null, false));
        assertThat(engine.askedOnFxThread()).containsExactly(false);
        assertThat(job.calls()).containsExactly("pauseAt", "subscribe", "run");
        awaitState(RunState.RUNNING);
        assertThat(preparing()).isFalse();
    }

    // IF a second press were accepted while the first was still preparing, THEN two runs would be built for one click.
    @Test
    void start_pressedTwiceWhilePreparing_isPreparedOnce() {
        openBookAndChooseModel();
        buildViewModel();

        press(viewModel::start);
        press(viewModel::start);

        assertThat(queued.pending()).isEqualTo(1);
    }

    // IF start were accepted during a run, THEN a second job would be built and refused after the model was loaded.
    @Test
    void start_whileRunning_isRefusedWithoutPreparingAnything() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        press(viewModel::start);

        assertThat(queued.pending()).isZero();
        assertThat(models.selections()).hasSize(1);
    }

    // IF a stopped run blocked start, THEN a person would have to restart the application to translate again.
    @Test
    void start_afterAStop_isAcceptedAsANewRun() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        press(viewModel::start);

        assertThat(queued.pending()).isEqualTo(1);
    }

    // IF a missing book started something anyway, THEN a run would be built from nothing; the message that names what
    // is missing is asserted in TranslatingViewModelRoutingTest.
    @Test
    void start_noBookOpen_isRefusedWithNoRunNoToastAndNoError() {
        chooseModel(MODEL);
        buildViewModel();

        press(viewModel::start);

        assertThat(queued.pending()).isZero();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(preparing()).isFalse();
    }

    // IF a missing model started something anyway, THEN the factory would be asked about a blank model name.
    @Test
    void start_noModelChosen_isRefusedWithNoRunNoToastAndNoError() {
        port.on(BOOK, Result.ok(BookFixtures.frankenstein()));
        buildViewModel();
        press(() -> imports.open(BOOK));

        press(viewModel::start);

        assertThat(queued.pending()).isZero();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
    }

    // IF a refused start disabled the control, THEN task 9.2 could not turn it into one that names what is missing.
    @Test
    void controls_noBookAndNoModel_stillOfferStart() {
        buildViewModel();

        assertThat(controls().start()).isEqualTo(ControlState.ENABLED);
    }

    // IF a model that cannot be created left the control busy, THEN start would stay dead until a restart; the
    // unreachable code belongs to the provider-error notice, not to a dialog.
    @Test
    void start_modelCannotBeCreated_showsTheProviderErrorClearsBusyAndBeginsNoRun() {
        openBookAndChooseModel();
        models = ScriptedChatModelFactory.failing(error());
        buildViewModel();

        press(viewModel::start);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).contains(new RunNotice.ProviderError(error()));
        assertThat(errors.presented()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertThat(job.calls()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(preparing()).isFalse();
        assertThat(controls().start()).isEqualTo(ControlState.ENABLED);
    }

    // IF a job that cannot be created left the control busy, THEN start would stay dead until a restart; the
    // unreachable code belongs to the provider-error notice, not to a dialog.
    @Test
    void start_jobCannotBeCreated_showsTheProviderErrorClearsBusyAndBeginsNoRun() {
        openBookAndChooseModel();
        engine = ScriptedTranslationEngine.failing(error());
        buildViewModel();

        press(viewModel::start);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(models.selections()).hasSize(1);
        assertThat(notice()).contains(new RunNotice.ProviderError(error()));
        assertThat(errors.presented()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(preparing()).isFalse();
    }

    // IF an executor that refuses the work left the control busy, THEN start would stay dead until a restart.
    @Test
    void start_executorRefusesTheWork_presentsAnInternalErrorAndClearsBusy() {
        openBookAndChooseModel();
        prepExecutor = Executors.newSingleThreadExecutor();
        prepExecutor.shutdown();
        buildViewModel();

        press(viewModel::start);

        assertThat(errors.presented()).hasSize(1);
        assertThat(errors.presented().get(0).code()).isEqualTo(ErrorCode.internal);
        assertThat(preparing()).isFalse();
        assertThat(state()).isEqualTo(RunState.IDLE);
    }
}
