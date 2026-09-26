package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The settings view model: its providers, its model, the check and the chips a report becomes.
 */
class SettingsViewModelTest extends SettingsViewModelTestBase {

    // IF a third provider were offered, or Ollama were not first and selected, THEN a person would meet the wrong
    // server on first launch.
    @Test
    void providers_freshViewModel_offersExactlyOllamaThenLmStudio() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        assertThat(onFx(() -> List.copyOf(viewModel.providers())))
                .containsExactly(
                        new ProviderRow("ollama", "http://localhost:11434"),
                        new ProviderRow("lmstudio", "http://localhost:1234/v1"));
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    // IF selecting one provider left the other selected, THEN two rows would both claim to be the current server.
    @Test
    void selectProvider_lmStudio_deselectsOllama() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            return null;
        });

        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("lmstudio");
    }

    // IF an id no row carries could be selected, THEN the check would be aimed at a server that is not listed.
    @Test
    void selectProvider_unknownId_keepsSelection() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        onFx(() -> {
            viewModel.selectProvider("nonexistent");
            return null;
        });

        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    // IF the check were offered without a model, THEN it would verify a server against nothing.
    @Test
    void checkAvailable_noModel_isFalse() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        assertThat(onFx(() -> viewModel.checkAvailable().get())).isFalse();
    }

    // IF choosing a model did not make the check available, THEN a person could never run it.
    @Test
    void checkAvailable_afterModelSet_isTrue() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        onFx(() -> {
            viewModel.model().set(MODEL);
            return null;
        });

        assertThat(onFx(() -> viewModel.checkAvailable().get())).isTrue();
    }

    // IF a check ran with no model chosen, THEN the verifier would be asked about an empty model id.
    @Test
    void check_noModel_doesNothing() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.idle();
        final SettingsViewModel viewModel = viewModel(verifier);

        onFx(() -> {
            viewModel.check();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verifier.callCount()).isZero();
        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
        assertThat(stagesOf(viewModel)).isEmpty();
    }

    // IF a failed first stage were shown with its later stages as passed, THEN a person would believe an unreachable
    // server had answered a listing and an inference.
    @Test
    void check_unreachableFirstStage_laterStagesSkippedNotPassed() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer.");
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(
                report(new StageOutcome(VerificationStage.CONNECTION, StageStatus.FAILED, unreachable, null)));
        final SettingsViewModel viewModel = viewModel(verifier);

        checkWithModel(viewModel);

        assertThat(stagesOf(viewModel))
                .extracting(StageChip::stage, StageChip::status)
                .containsExactly(
                        tuple(VerificationStage.CONNECTION, StageStatus.FAILED),
                        tuple(VerificationStage.MODELS, StageStatus.SKIPPED),
                        tuple(VerificationStage.INFERENCE, StageStatus.SKIPPED));
        assertThat(stagesOf(viewModel).get(0).error()).isSameAs(unreachable);
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a report stopping after the second failed stage were shown with two chips, THEN the third stage would
    // silently vanish instead of reading as not attempted.
    @Test
    void check_secondStageFails_thirdStagePaddedAsSkipped() {
        final AppError missing = AppError.of(ErrorCode.modelNotFound, "Model not found", "The model is not installed.");
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(report(
                outcome(VerificationStage.CONNECTION, StageStatus.PASSED),
                new StageOutcome(VerificationStage.MODELS, StageStatus.FAILED, missing, null)));
        final SettingsViewModel viewModel = viewModel(verifier);

        checkWithModel(viewModel);

        assertThat(stagesOf(viewModel))
                .extracting(StageChip::stage, StageChip::status)
                .containsExactly(
                        tuple(VerificationStage.CONNECTION, StageStatus.PASSED),
                        tuple(VerificationStage.MODELS, StageStatus.FAILED),
                        tuple(VerificationStage.INFERENCE, StageStatus.SKIPPED));
    }

    // IF a qualified pass in the model stage stopped the check, THEN the inference stage would never be reported and a
    // usable server would look half-verified.
    @Test
    void check_softPassModelsStage_stillAttemptsInference() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(report(
                outcome(VerificationStage.CONNECTION, StageStatus.PASSED),
                new StageOutcome(VerificationStage.MODELS, StageStatus.SOFT_PASS, null, "not in the server's list"),
                outcome(VerificationStage.INFERENCE, StageStatus.PASSED)));
        final SettingsViewModel viewModel = viewModel(verifier);

        checkWithModel(viewModel);

        assertThat(stagesOf(viewModel))
                .extracting(StageChip::stage, StageChip::status, StageChip::note)
                .containsExactly(
                        tuple(VerificationStage.CONNECTION, StageStatus.PASSED, null),
                        tuple(VerificationStage.MODELS, StageStatus.SOFT_PASS, "not in the server's list"),
                        tuple(VerificationStage.INFERENCE, StageStatus.PASSED, null));
        assertThat(verifier.selections()).containsExactly(new ModelSelection("ollama", MODEL));
        assertThat(verifier.policies()).containsExactly(VerificationPolicy.FULL);
        assertThat(toasts.raised())
                .containsExactly(new RecordingToasts.Raised("success", MessageKey.TOAST_PROVIDER_PASSED, List.of()));
    }

    // IF a fully passing check raised no success message, or more than one, THEN a person would get no confirmation
    // or a duplicated one.
    @Test
    void check_allStagesPass_raisesOneSuccessToast() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));

        checkWithModel(viewModel);

        assertThat(toasts.raised())
                .containsExactly(new RecordingToasts.Raised("success", MessageKey.TOAST_PROVIDER_PASSED, List.of()));
        assertThat(errors.presented()).isEmpty();
    }

    // IF a failed check still congratulated the person, THEN the toast would contradict the failed chip beside it.
    @Test
    void check_failedStage_raisesNoToast() {
        final AppError refused = AppError.of(ErrorCode.auth, "Not authorised", "The server refused the key.");
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(report(
                outcome(VerificationStage.CONNECTION, StageStatus.PASSED),
                outcome(VerificationStage.MODELS, StageStatus.PASSED),
                new StageOutcome(VerificationStage.INFERENCE, StageStatus.FAILED, refused, null))));

        checkWithModel(viewModel);

        assertThat(toasts.raised()).isEmpty();
        assertThat(stagesOf(viewModel)).extracting(StageChip::status).contains(StageStatus.FAILED);
    }

    // IF a defect in the verifier left the in-progress mark set, THEN the check could never be run again; the
    // defect must surface as an internal error instead of vanishing.
    @Test
    void check_verifierThrows_clearsInProgressAndPresentsInternalError() {
        final SettingsViewModel viewModel =
                viewModel(ScriptedProviderVerifier.throwing(new IllegalStateException("adapter defect")));

        checkWithModel(viewModel);

        assertThat(onFx(() -> viewModel.checking().get())).isFalse();
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isTrue();
        assertThat(errors.presented()).extracting(AppError::code).containsExactly(ErrorCode.internal);
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a model chosen for one server stayed when another was selected, THEN the check would ask the new server for
    // a model it may not have, and show the old server's verdict beside it.
    @Test
    void selectProvider_afterModelChosen_clearsModelAndStages() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));
        checkWithModel(viewModel);

        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            return null;
        });

        assertThat(onFx(() -> viewModel.model().get())).isEmpty();
        assertThat(stagesOf(viewModel)).isEmpty();
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isFalse();
    }

    // IF re-selecting the current provider cleared the model, THEN clicking the already-selected row would silently
    // discard what the person had typed.
    @Test
    void selectProvider_sameProvider_keepsModelAndStages() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));
        checkWithModel(viewModel);

        onFx(() -> {
            viewModel.selectProvider("ollama");
            return null;
        });

        assertThat(onFx(() -> viewModel.model().get())).isEqualTo(MODEL);
        assertThat(stagesOf(viewModel)).hasSize(3);
    }
}
