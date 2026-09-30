package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedTranslationEngine;

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
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        press(() -> imports.open(BOOK));
        press(() -> brief.setTargetLanguage("uk"));
    }

    private void openBookDeclaringNoLanguageAndChooseModel() {
        projects.on(NOTES, Result.ok(BookFixtures.declaringNoLanguage("notes", BookFormat.TXT, 1)));
        press(() -> imports.open(NOTES));
        chooseModel(MODEL);
    }

    private static final Path NOTES = Path.of("notes.txt");

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

    // --- the notice belongs to the run that raised it ---------------------------------------------------------

    // IF the last run's notice stayed while a new run began, THEN a healthy run would still read as failed.
    @Test
    void start_afterAProviderFailure_clearsTheNoticeAtOnce() {
        openBookAndChooseModel();
        models = ScriptedChatModelFactory.failing(failureOf(ErrorCode.unreachable));
        buildViewModel();
        press(viewModel::start);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(noticeKind()).isEqualTo(PROVIDER);

        press(viewModel::start);

        assertThat(notice()).isEmpty();
    }

    // IF the notice came back once the new run reported running, THEN a run in progress would show the old failure.
    @Test
    void run_startsAfterARefusedRun_showsNoNoticeOnceRunning() throws Exception {
        final RecordingJob again = new RecordingJob();
        engine = ScriptedTranslationEngine.returning(job, again);
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        job.finish(Result.err(failureOf(ErrorCode.validation)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(noticeKind()).isEqualTo("Refused");

        press(viewModel::start);
        queued.runAll();
        awaitState(RunState.RUNNING);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        again.finish(Result.ok(cancelledReport()));
    }

    // --- a start with a language missing ----------------------------------------------------------------------

    // IF a book declaring no language could start, THEN the model would be told a source language nobody chose.
    @Test
    void start_noSourceLanguage_isRefusedNamingTheSourceAndAsksForNoJob() {
        buildViewModel();
        openBookDeclaringNoLanguageAndChooseModel();
        press(() -> brief.setTargetLanguage("uk"));

        press(viewModel::start);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.SOURCE_LANGUAGE));
        assertThat(engine.requests()).isEmpty();
    }

    // IF a start with no target language asked for a job, THEN the run would translate into no language.
    @Test
    void start_sourceButNoTargetLanguage_isRefusedNamingTheTargetAndAsksForNoJob() {
        buildViewModel();
        openBookDeclaringNoLanguageAndChooseModel();
        press(() -> brief.setSourceLanguage("en"));

        press(viewModel::start);

        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.TARGET_LANGUAGE));
        assertThat(engine.requests()).isEmpty();
    }

    // IF choosing the source left "choose the source language" on screen, THEN the person would be told to do what
    // they just did.
    @Test
    void notice_missingSourceThenSourceChosen_isWithdrawn() {
        buildViewModel();
        openBookDeclaringNoLanguageAndChooseModel();
        press(() -> brief.setTargetLanguage("uk"));
        press(viewModel::start);
        assertThat(notice()).contains(new RunNotice.MissingInput(RunNotice.Input.SOURCE_LANGUAGE));

        press(() -> brief.setSourceLanguage("en"));

        assertThat(notice()).isEmpty();
    }
}
