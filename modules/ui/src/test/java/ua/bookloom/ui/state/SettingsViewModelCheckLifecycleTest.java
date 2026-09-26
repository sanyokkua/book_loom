package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.ScriptedProviderVerifier;

/**
 * The life of one check on the settings view model: while it runs, how a whole-check refusal is routed, and how a
 * report that no longer matches the selection is dropped.
 */
class SettingsViewModelCheckLifecycleTest extends SettingsViewModelTestBase {

    // IF the check did not report itself in progress while the verifier had not answered, THEN a person could not
    // tell the action had been accepted, and could fire it a second time; and IF the earlier chips stayed while a new
    // check ran, THEN an old verdict would sit beside a check that has not answered.
    @Test
    void check_verifierNotYetAnswered_reportsInProgressAndDisablesControl()
            throws InterruptedException, TimeoutException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        useCountingPool();
        final SettingsViewModel viewModel = viewModel(verifier);
        startCheck(viewModel);
        verifier.awaitEntered();
        verifier.release();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> stagesOf(viewModel).size() == 3);
        assertThat(onFx(() -> viewModel.checking().get())).isFalse();

        verifier.hold();
        onFx(() -> {
            viewModel.check();
            return null;
        });
        verifier.awaitCalls(2);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> viewModel.checking().get())).isTrue();
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isFalse();
        assertThat(stagesOf(viewModel)).isEmpty();
        verifier.release();
    }

    // IF the in-progress mark stayed after the report arrived, THEN the control would stay locked for good.
    @Test
    void check_afterReport_clearsInProgress() throws InterruptedException, TimeoutException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        useCountingPool();
        final SettingsViewModel viewModel = viewModel(verifier);
        onFx(() -> {
            viewModel.model().set(MODEL);
            viewModel.check();
            return null;
        });
        verifier.awaitEntered();

        verifier.release();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !onFx(() -> viewModel.checking().get()));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isTrue();
        assertThat(stagesOf(viewModel)).hasSize(3);
    }

    // IF a refusal a person can fix opened a dialog, or were drawn as stage chips, THEN the specified in-place
    // treatment would be missing and a stage verdict that no stage produced would show.
    @Test
    void check_topLevelValidationError_isShownInPlaceNotAsDialogOrChips() {
        final AppError refusal = AppError.of(ErrorCode.validation, "Unknown provider", "No such provider.");
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(Result.err(refusal)));

        checkWithModel(viewModel);

        assertThat(onFx(() -> viewModel.checkRefusal().get())).isEqualTo("No such provider.");
        assertThat(errors.presented()).isEmpty();
        assertThat(stagesOf(viewModel)).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
    }

    // IF an unexpected or busy refusal were only written in place, THEN it would lack the expandable-details dialog
    // the notification spec gives it.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"internal", "busy"})
    void check_topLevelInternalOrBusy_goesToThePresenter(final ErrorCode code) {
        final AppError refusal = AppError.of(code, "Refused", "The check was refused.");
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(Result.err(refusal)));

        checkWithModel(viewModel);

        assertThat(errors.presented()).containsExactly(refusal);
        assertThat(onFx(() -> viewModel.checkRefusal().get())).isEmpty();
        assertThat(stagesOf(viewModel)).isEmpty();
    }

    // IF a stale refusal line stayed after the model changed, THEN it would blame a choice the person already changed.
    @Test
    void checkRefusal_modelChanged_isCleared() {
        final AppError refusal = AppError.of(ErrorCode.validation, "Unknown provider", "No such provider.");
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(Result.err(refusal)));
        checkWithModel(viewModel);

        onFx(() -> {
            viewModel.model().set("another-model");
            return null;
        });

        assertThat(onFx(() -> viewModel.checkRefusal().get())).isEmpty();
    }

    // IF a check whose provider was switched away and back reported anyway, THEN its verdict would sit against a
    // selection it was never made for, with a success toast for a check the person no longer sees.
    @Test
    void check_providerSwitchedAwayAndBackMidCheck_reportDiscarded() throws InterruptedException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        final CountingPool pool = useCountingPool();
        final SettingsViewModel viewModel = viewModel(verifier);
        startCheck(viewModel);
        verifier.awaitEntered();

        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            viewModel.selectProvider("ollama");
            viewModel.model().set(MODEL);
            return null;
        });
        verifier.release();
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stagesOf(viewModel)).isEmpty();
        assertThat(toasts.raised()).isEmpty();
        assertThat(errors.presented()).isEmpty();
        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
    }

    // IF the selection still looked busy after the provider changed mid-check, THEN a person could not check the new
    // provider until the old server answered.
    @Test
    void selectProvider_midCheck_clearsCheckingAtOnceAndAvailabilityFollowsModel() throws InterruptedException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        useCountingPool();
        final SettingsViewModel viewModel = viewModel(verifier);
        startCheck(viewModel);
        verifier.awaitEntered();
        assertThat(onFx(() -> viewModel.checking().get())).isTrue();

        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            return null;
        });

        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isFalse();
        onFx(() -> {
            viewModel.model().set(MODEL);
            return null;
        });
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isTrue();
        verifier.release();
    }

    // IF the model changing mid-check left the check counted as running, THEN the new model could not be checked.
    @Test
    void model_changedMidCheck_clearsChecking() throws InterruptedException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        useCountingPool();
        final SettingsViewModel viewModel = viewModel(verifier);
        startCheck(viewModel);
        verifier.awaitEntered();

        onFx(() -> {
            viewModel.model().set("another-model");
            return null;
        });

        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isTrue();
        verifier.release();
    }

    // IF an old check's arrival cleared the mark of a newer check, THEN the control would unlock while the newer
    // check was still running, and the newer check's report would land on an unlocked screen.
    @Test
    void check_olderCompletesWhileNewerRuns_newerStillCheckingUntilItsOwnReport() throws InterruptedException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        final CountingPool pool = useCountingPool();
        final SettingsViewModel viewModel = viewModel(verifier);
        startCheck(viewModel);
        verifier.awaitEntered();
        verifier.hold();
        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            viewModel.model().set(MODEL);
            viewModel.check();
            return null;
        });
        verifier.awaitCalls(2);

        verifier.release();
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> viewModel.checking().get())).isTrue();
        assertThat(stagesOf(viewModel)).isEmpty();
        assertThat(toasts.raised()).isEmpty();

        verifier.release();
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
        assertThat(stagesOf(viewModel)).hasSize(3);
        assertThat(toasts.raised()).hasSize(1);
    }
}
