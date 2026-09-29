package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.RecordingToasts.Raised;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedTranslationEngine;
import ua.bookloom.ui.i18n.MessageKey;

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
        assertThat(projects.imports()).containsExactly(BOOK);
        assertThat(projects.briefs())
                .extracting(BookBrief::sourceLanguage, BookBrief::targetLanguage)
                .containsExactly(tuple("en", "uk"));
        assertThat(engine.requests()).containsExactly(new RunRequest("p1", ReviewMode.UNATTENDED));
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

    // IF a stopped run also offered a start, THEN a person could begin a second run beside the resume that
    // continues the first one.
    @Test
    void start_afterAStop_isRefusedBecauseOnlyResumeIsOffered() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);

        press(viewModel::start);

        assertThat(queued.pending()).isZero();
    }

    // IF the pause points of the review mode were not handed to the job, THEN an Assisted run would never stop on a
    // flagged segment; a model error always pauses, whatever the mode.
    @ParameterizedTest
    @MethodSource("pausePointsByMode")
    void start_eachReviewMode_asksForOneJobWithTheModesPausePointsAndOnError(
            final ReviewMode mode, final Set<PausePoint> expected) throws Exception {
        reviewMode = mode;
        openBookAndChooseModel();
        buildViewModel();

        startAndPrepare();

        assertThat(engine.requests()).containsExactly(new RunRequest("p1", mode));
        assertThat(job.pausePoints()).isEqualTo(expected);
    }

    static Stream<Arguments> pausePointsByMode() {
        return Stream.of(
                Arguments.of(ReviewMode.UNATTENDED, Set.of(PausePoint.ON_ERROR)),
                Arguments.of(ReviewMode.ASSISTED, Set.of(PausePoint.ON_FLAGGED, PausePoint.ON_ERROR)),
                Arguments.of(
                        ReviewMode.MANUAL,
                        Set.of(PausePoint.AFTER_SEGMENT, PausePoint.ON_FLAGGED, PausePoint.ON_ERROR)));
    }

    // IF a run began silently, THEN a person would not learn that closing the application loses its progress.
    @Test
    void start_ready_raisesTheRunStartedToastOnce() throws Exception {
        openBookAndChooseModel();
        buildViewModel();

        startAndPrepare();

        assertThat(toasts.raised()).containsExactly(new Raised("info", MessageKey.TOAST_RUN_STARTED, List.of()));
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
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        buildViewModel();
        press(() -> imports.open(BOOK));

        press(viewModel::start);

        assertThat(queued.pending()).isZero();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
    }

    // IF a refused start disabled the control, THEN the person could not press it again to be told what is missing.
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
