package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;

/**
 * The notice a refused start or a failed run leaves on the dashboard: a refused start names its missing input, and
 * the notice is withdrawn as soon as it stops being true.
 */
class TranslatingViewModelNoticeTest extends TranslatingViewModelTestBase {

    private void assertNothingStarted() {
        assertThat(queued.pending()).isZero();
        assertThat(models.selections()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertThat(job.calls()).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(preparing()).isFalse();
    }

    private void openBookWithoutChoosingAModel() {
        port.on(BOOK, Result.ok(BookFixtures.frankenstein()));
        press(() -> imports.open(BOOK));
    }

    // --- a start with an input missing ------------------------------------------------------------------------

    // IF a start with no model did nothing, THEN a person would press Start and be told nothing about why.
    @Test
    void start_noModelChosen_publishesTheMissingModelAndStartsNothing() {
        buildViewModel();
        openBookWithoutChoosingAModel();

        press(viewModel::start);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.MODEL));
        assertNothingStarted();
    }

    // IF a start with no book did nothing, THEN a person would press Start and be told nothing about why.
    @Test
    void start_noBookOpen_publishesTheMissingBookAndStartsNothing() {
        chooseModel(MODEL);
        buildViewModel();

        press(viewModel::start);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.BOOK));
        assertNothingStarted();
    }

    // IF the model were named before the book, THEN the message would send a person to fix the later step first.
    @Test
    void start_bookAndModelBothMissing_namesTheBookFirst() {
        buildViewModel();

        press(viewModel::start);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.BOOK));
        assertNothingStarted();
    }

    // IF a refused start were reported as a provider failure, THEN a person would go and check a server that is fine.
    @Test
    void start_anInputIsMissing_isNotShownAsAProviderErrorNorAsADialog() {
        buildViewModel();
        openBookWithoutChoosingAModel();

        press(viewModel::start);

        assertThat(noticeKind()).isEqualTo("MissingInput");
        assertThat(errors.presented()).isEmpty();
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a refusal stayed on screen once the missing input was supplied, THEN a ready start would still read as
    // refused.
    @Test
    void start_afterTheMissingModelIsChosen_clearsTheRefusalAndBeginsPreparing() {
        buildViewModel();
        openBookWithoutChoosingAModel();
        press(viewModel::start);
        chooseModel(MODEL);

        press(viewModel::start);

        assertThat(notice()).isEmpty();
        assertThat(queued.pending()).isEqualTo(1);
        assertThat(preparing()).isTrue();
    }

    // IF choosing the model left "choose a model" on screen, THEN a ready start would still read as refused.
    @Test
    void notice_missingModelThenModelChosen_isWithdrawn() {
        buildViewModel();
        openBookWithoutChoosingAModel();
        press(viewModel::start);
        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.MODEL));

        chooseModel(MODEL);

        assertThat(notice()).isEmpty();
    }

    // IF opening the book left "open a book" on screen, THEN the person would be told to do what they just did.
    @Test
    void notice_missingBookThenBookOpened_isWithdrawn() {
        chooseModel(MODEL);
        buildViewModel();
        press(viewModel::start);
        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.BOOK));

        openBookWithoutChoosingAModel();

        assertThat(notice()).isEmpty();
    }

    // IF a still-missing input were withdrawn by supplying another, THEN the refusal would vanish while it is true.
    @Test
    void notice_missingBookThenOnlyAModelChosen_stillNamesTheBook() {
        buildViewModel();
        press(viewModel::start);

        chooseModel(MODEL);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.BOOK));
    }

    // IF an unusable destination returned before the notice was cleared, THEN a stale "choose a model" banner would
    // stay although the model is chosen.
    @Test
    void start_afterMissingModelThenUnusableDestination_leavesNoStaleNotice() {
        buildViewModel();
        openBookWithoutChoosingAModel();
        press(viewModel::start);
        chooseModel(MODEL);
        press(() -> brief.editDestination(""));

        press(viewModel::start);

        assertThat(notice()).isEmpty();
        assertThat(queued.pending()).isZero();
        assertThat(preparing()).isFalse();
    }

    // --- the notice belongs to the run that raised it ---------------------------------------------------------

    // IF the last run's notice stayed while a new run began, THEN a healthy run would still read as failed.
    @Test
    void start_afterAProviderFailure_clearsTheNoticeAtOnce() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        job.finish(Result.err(failureOf(ErrorCode.unreachable)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(noticeKind()).isEqualTo(PROVIDER);

        press(viewModel::start);

        assertThat(notice()).isEmpty();
    }

    // IF the notice came back once the new run reported running, THEN a run in progress would show the old failure.
    @Test
    void run_startsAfterAProviderFailure_showsNoNoticeOnceRunning() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        job.finish(Result.err(failureOf(ErrorCode.unreachable)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();

        press(viewModel::start);
        queued.runAll();
        awaitState(RunState.RUNNING);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        job.finish(Result.ok(cancelledReport()));
    }
}
