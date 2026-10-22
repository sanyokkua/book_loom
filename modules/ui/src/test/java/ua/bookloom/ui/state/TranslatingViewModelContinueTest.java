package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.dialog.RecordingContinuePrompt;

/**
 * Where the continue questions sit in a start and a resume after a stop: after the missing-input check, before
 * anything is prepared, with the screen busy while they are open. The guard is the real one; see
 * {@link ContinueGuardTest} for what each answer does.
 */
class TranslatingViewModelContinueTest extends TranslatingViewModelTestBase {

    private static final String STORED = "gemma4:e4b";

    private void storedRunUsed(final String modelId) {
        projects.willList(List.of(ProjectFixtures.summary(
                ProjectFixtures.project(
                        "p1",
                        "Frankenstein.epub",
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        NameOrigin.AUTO,
                        "Frankenstein",
                        Instant.parse("2026-10-21T10:00:00Z")),
                3,
                new ua.bookloom.api.project.RunRecord(
                        "run-p1",
                        "p1",
                        Instant.parse("2026-10-21T10:00:00Z"),
                        null,
                        JobState.PAUSED,
                        3,
                        0,
                        new ModelSelection("ollama", modelId)),
                null)));
    }

    // IF a different model started the rest of a book silently, THEN the book would read as two translators' work.
    @Test
    void start_storedModelDiffers_asksBeforePreparingAndStaysBusy() {
        openBookAndChooseModel();
        storedRunUsed(STORED);
        buildViewModel();

        press(viewModel::start);

        assertThat(continuePrompt.kinds()).containsExactly(RecordingContinuePrompt.DIFFERENT);
        assertThat(queued.pending()).isZero();
        assertThat(models.selections()).isEmpty();
        assertThat(preparing()).isTrue();
    }

    @Test
    void start_differentModelUseEarlier_runsWithTheEarlierModel() throws Exception {
        openBookAndChooseModel();
        storedRunUsed(STORED);
        catalog.respondWith(STORED);
        buildViewModel();
        press(viewModel::start);

        press(continuePrompt::useEarlier);
        queued.runAll();
        job.awaitRunStarted();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(models.selections()).containsExactly(new ModelSelection("ollama", STORED));
        assertThat(engine.requests()).extracting(RunRequest::modelName).containsExactly(STORED);
    }

    @Test
    void start_differentModelUseNew_runsWithTheChosenModel() throws Exception {
        openBookAndChooseModel();
        storedRunUsed(STORED);
        catalog.respondWith(MODEL);
        buildViewModel();
        press(viewModel::start);

        press(continuePrompt::useNew);
        queued.runAll();
        job.awaitRunStarted();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(models.selections()).containsExactly(new ModelSelection("ollama", MODEL));
    }

    // IF Escape left the screen busy, THEN Start would stay disabled until the app restarted.
    @Test
    void start_differentModelEscape_startsNothingAndClearsTheBusyState() {
        openBookAndChooseModel();
        storedRunUsed(STORED);
        buildViewModel();
        press(viewModel::start);

        press(continuePrompt::escape);

        assertThat(preparing()).isFalse();
        assertThat(queued.pending()).isZero();
        assertThat(models.selections()).isEmpty();
    }

    @Test
    void start_storedModelNotAnswering_asksAndPreparesNothingUntilItAnswers() throws Exception {
        openBookAndChooseModel();
        chooseModel(STORED);
        storedRunUsed(STORED);
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Down", "Nothing answers."));
        buildViewModel();

        press(viewModel::start);

        assertThat(continuePrompt.kinds()).containsExactly(RecordingContinuePrompt.NOT_ANSWERING);
        assertThat(queued.pending()).isZero();
        assertThat(preparing()).isTrue();

        catalog.respondWith(STORED);
        press(continuePrompt::tryAgain);
        queued.runAll();
        job.awaitRunStarted();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(models.selections()).containsExactly(new ModelSelection("ollama", STORED));
    }

    @Test
    void start_notAnsweringAndNotNow_startsNothingAndClearsTheBusyState() {
        openBookAndChooseModel();
        chooseModel(STORED);
        storedRunUsed(STORED);
        catalog.respondWith(AppError.of(ErrorCode.unreachable, "Down", "Nothing answers."));
        buildViewModel();
        press(viewModel::start);

        press(continuePrompt::notNow);

        assertThat(preparing()).isFalse();
        assertThat(models.selections()).isEmpty();
    }

    @Test
    void start_sameModelAnswering_runsWithoutAnyQuestion() throws Exception {
        openBookAndChooseModel();
        chooseModel(STORED);
        storedRunUsed(STORED);
        catalog.respondWith(STORED);
        buildViewModel();

        startAndPrepare();

        assertThat(continuePrompt.asked()).isEmpty();
        assertThat(models.selections()).containsExactly(new ModelSelection("ollama", STORED));
    }

    // IF a first run, with no stored model, were asked about anything, THEN every new book would open with a question.
    @Test
    void start_noStoredRun_asksNothingAndTestsNothing() throws Exception {
        openBookAndChooseModel();
        buildViewModel();

        startAndPrepare();

        assertThat(continuePrompt.asked()).isEmpty();
        assertThat(catalog.callCount()).isZero();
    }

    // IF a start with missing input asked a model question first, THEN the person would answer it and still be refused.
    @Test
    void start_noModelChosen_isRefusedForTheMissingInputWithoutAnyQuestion() {
        openBook();
        storedRunUsed(STORED);
        buildViewModel();

        press(viewModel::start);

        assertThat(continuePrompt.asked()).isEmpty();
        assertThat(catalog.callCount()).isZero();
        assertThat(preparing()).isFalse();
    }

    @Test
    void resume_afterAStopWithADifferentChosenModel_asksBeforePreparing() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);
        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
        storedRunUsed(STORED);

        press(viewModel::resume);

        assertThat(continuePrompt.kinds()).containsExactly(RecordingContinuePrompt.DIFFERENT);
        assertThat(queued.pending()).isZero();
    }

    // IF a resume from a pause asked, THEN the live run's own model would be questioned mid-run.
    @Test
    void resume_pausedRun_neverAsks() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::pause);
        job.emit(new Paused(PauseReason.REQUESTED, null, progress(5, 0, 5)));
        deliverAndTick();
        awaitState(RunState.PAUSED);
        storedRunUsed(STORED);

        press(viewModel::resume);

        assertThat(continuePrompt.asked()).isEmpty();
        assertThat(job.calls()).contains("resume");
        assertThat(onFx(() -> viewModel.preparing().get())).isFalse();
    }
}
