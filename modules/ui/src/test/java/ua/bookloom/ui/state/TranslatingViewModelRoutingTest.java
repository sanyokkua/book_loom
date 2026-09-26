package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedTranslationEngine;

/**
 * Where each failure appears: every failure a run or its preparation ends with reaches exactly the one surface the
 * notifications spec assigns its code.
 */
class TranslatingViewModelRoutingTest extends TranslatingViewModelTestBase {

    private static final String REFUSED = "Refused";

    // Written out code by code from the notifications spec: the notice a code produces, and how many times the error
    // presenter is handed it.
    static Stream<Arguments> assignedSurfaces() {
        return Stream.of(
                Arguments.of(ErrorCode.unreachable, PROVIDER, 0),
                Arguments.of(ErrorCode.timeout, PROVIDER, 0),
                Arguments.of(ErrorCode.auth, PROVIDER, 0),
                Arguments.of(ErrorCode.rateLimited, PROVIDER, 0),
                Arguments.of(ErrorCode.upstream, PROVIDER, 0),
                Arguments.of(ErrorCode.emptyCompletion, PROVIDER, 0),
                Arguments.of(ErrorCode.modelNotFound, PROVIDER, 0),
                Arguments.of(ErrorCode.modelUnavailable, PROVIDER, 0),
                Arguments.of(ErrorCode.missingCredential, PROVIDER, 0),
                Arguments.of(ErrorCode.contextWindow, PROVIDER, 0),
                Arguments.of(ErrorCode.cancelled, NONE, 0),
                Arguments.of(ErrorCode.validation, REFUSED, 0),
                Arguments.of(ErrorCode.internal, NONE, 1),
                Arguments.of(ErrorCode.busy, NONE, 1),
                Arguments.of(ErrorCode.discoveryFailed, NONE, 0));
    }

    // A cancelled result ends the run stopped, every other code ends it failed.
    private void awaitEnded() throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> mirror.runState().get()) != RunState.RUNNING);
    }

    // --- a run that returns a failure -------------------------------------------------------------------------

    // IF any of the fifteen codes reached a surface other than its assigned one, or reached two, THEN one failure
    // would be told twice or told wrongly.
    @ParameterizedTest(name = "{0} -> notice {1}, {2} dialog(s)")
    @MethodSource("assignedSurfaces")
    void run_returnsAFailure_reachesExactlyTheSurfaceAssignedToItsCode(
            final ErrorCode code, final String expectedNotice, final int expectedDialogs) throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(failureOf(code)));
        awaitEnded();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(noticeKind()).isEqualTo(expectedNotice);
        assertThat(errors.presented()).hasSize(expectedDialogs);
        assertThat(toasts.raised()).isEmpty();
    }

    // IF the provider-error state dropped the failure it names, THEN the banner could not say which code ended the run.
    @Test
    void run_unreachable_publishesAProviderErrorCarryingThatFailureAndOpensNoDialog() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(failureOf(ErrorCode.unreachable)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).contains(new RunNotice.ProviderError(failureOf(ErrorCode.unreachable)));
        assertThat(errors.presented()).isEmpty();
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a failure reset the figures, THEN the person could not see how much was done before it fell over.
    @Test
    void run_unreachableAfter412Accepted_keepsTheCountsAndOpensNoDialog() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        emitAccepted(job, 1, 412);
        deliverAndTick();
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(412);

        job.finish(Result.err(failureOf(ErrorCode.unreachable)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(412);
        assertThat(noticeKind()).isEqualTo(PROVIDER);
        assertThat(errors.presented()).isEmpty();
    }

    // IF a destination that already exists were shown as a provider failure, THEN a person would fix a machine that
    // was never broken.
    @Test
    void run_refusedWithValidation_isRefusedInPlaceAndNotAProviderError() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        final AppError refusal = AppError.of(
                ErrorCode.validation, "Destination exists", "The destination already exists and may not be replaced.");

        job.finish(Result.err(refusal));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).contains(new RunNotice.Refused(refusal));
        assertThat(errors.presented()).isEmpty();
        assertThat(toasts.raised()).isEmpty();
    }

    // IF an unexpected failure were shown only as a banner, THEN a bug would read like a routine provider hiccup.
    @Test
    void run_internalFailure_isPresentedToTheErrorPresenterOnceAndShowsNoNotice() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(failureOf(ErrorCode.internal)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(errors.presented()).containsExactly(failureOf(ErrorCode.internal));
        assertThat(notice()).isEmpty();
    }

    // IF a busy failure were shown only as a banner, THEN the blocking failure the spec names would not block.
    @Test
    void run_busyFailure_isPresentedToTheErrorPresenterOnceAndShowsNoNotice() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(failureOf(ErrorCode.busy)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(errors.presented()).containsExactly(failureOf(ErrorCode.busy));
        assertThat(notice()).isEmpty();
    }

    // IF a cancellation were dressed as an error, THEN a choice the person made would be reported as if it had gone
    // wrong; the spec shows it as the run's stopped state.
    @Test
    void run_cancelledFailure_endsStoppedAndTouchesNeitherTheErrorPresenterNorTheToastsNorTheNotice() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(failureOf(ErrorCode.cancelled)));
        awaitState(RunState.STOPPED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(errors.presented()).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(notice()).isEmpty();
    }

    // IF a stopped run (a returned cancelled report) raised a notice, THEN a stop would look like a failure.
    @Test
    void run_stoppedByThePerson_showsNoNoticeAndNoDialog() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();
        press(viewModel::stop);

        job.finish(Result.ok(cancelledReport()));
        awaitState(RunState.STOPPED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a discovery failure opened a dialog or drew a provider error, THEN a note that belongs to the model list would
    // be shown as the run's own failure.
    @Test
    void run_discoveryFailed_opensNoDialogAndRaisesNoToastOrNotice() throws Exception {
        openBookAndChooseModel();
        buildViewModel();
        startAndPrepare();

        job.finish(Result.err(failureOf(ErrorCode.discoveryFailed)));
        awaitState(RunState.FAILED);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(errors.presented()).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(notice()).isEmpty();
    }

    // --- a run that could not be prepared ---------------------------------------------------------------------

    // IF a failed preparation were routed differently from the same failure ending a run, THEN the same code would
    // appear in two places depending on when it happened.
    @ParameterizedTest(name = "{0} -> notice {1}, {2} dialog(s)")
    @MethodSource("assignedSurfaces")
    void start_modelCannotBeCreated_reachesExactlyTheSurfaceAssignedToItsCode(
            final ErrorCode code, final String expectedNotice, final int expectedDialogs) {
        openBookAndChooseModel();
        models = ScriptedChatModelFactory.failing(failureOf(code));
        buildViewModel();

        press(viewModel::start);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(noticeKind()).isEqualTo(expectedNotice);
        assertThat(errors.presented()).hasSize(expectedDialogs);
        assertThat(toasts.raised()).isEmpty();
        assertThat(engine.requests()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(preparing()).isFalse();
    }

    // IF a job that cannot be created were sent to the dialog whatever its code, THEN a refused destination would be
    // shown like a crash.
    @ParameterizedTest(name = "{0} -> notice {1}, {2} dialog(s)")
    @MethodSource("assignedSurfaces")
    void start_jobCannotBeCreated_reachesExactlyTheSurfaceAssignedToItsCode(
            final ErrorCode code, final String expectedNotice, final int expectedDialogs) {
        openBookAndChooseModel();
        engine = ScriptedTranslationEngine.failing(failureOf(code));
        buildViewModel();

        press(viewModel::start);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(noticeKind()).isEqualTo(expectedNotice);
        assertThat(errors.presented()).hasSize(expectedDialogs);
        assertThat(toasts.raised()).isEmpty();
        assertThat(job.calls()).isEmpty();
        assertThat(state()).isEqualTo(RunState.IDLE);
        assertThat(preparing()).isFalse();
    }

    // IF a model the server does not have were shown as a dialog, THEN the person would get no route to the settings.
    @Test
    void start_modelNotFound_publishesAProviderErrorCarryingThatFailureAndOpensNoDialog() {
        openBookAndChooseModel();
        models = ScriptedChatModelFactory.failing(failureOf(ErrorCode.modelNotFound));
        buildViewModel();

        press(viewModel::start);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).contains(new RunNotice.ProviderError(failureOf(ErrorCode.modelNotFound)));
        assertThat(errors.presented()).isEmpty();
    }

    // IF a destination the engine refuses were shown as a provider error, THEN the settings offer would mislead.
    @Test
    void start_engineRefusesTheDestinationWithValidation_isRefusedInPlace() {
        openBookAndChooseModel();
        final AppError refusal = failureOf(ErrorCode.validation);
        engine = ScriptedTranslationEngine.failing(refusal);
        buildViewModel();

        press(viewModel::start);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(notice()).contains(new RunNotice.Refused(refusal));
        assertThat(errors.presented()).isEmpty();
    }
}
