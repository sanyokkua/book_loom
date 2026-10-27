package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ScriptedProviderVerifier;

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
                        new ProviderRow("ollama", ProviderKind.OLLAMA, "http://localhost:11434", "localhost:11434", ""),
                        new ProviderRow(
                                "lmstudio",
                                ProviderKind.OPENAI_COMPATIBLE,
                                "http://localhost:1234/v1",
                                "localhost:1234",
                                ""));
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    // IF opening another provider's form moved the current provider, THEN a person looking at LM Studio would
    // silently start translating with it.
    @Test
    void editProvider_lmStudio_keepsOllamaCurrent() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });

        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("lmstudio");
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    // IF an id no row carries could be edited, THEN the form would describe a server that is not listed.
    @Test
    void editProvider_unknownId_keepsEditedProvider() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        onFx(() -> {
            viewModel.editProvider("nonexistent");
            return null;
        });

        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("ollama");
    }

    // IF the first row to open were not the current provider, THEN the form would start on a provider the badge
    // does not call current.
    @Test
    void editedProviderId_afterLoad_isTheCurrentProvider() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("ollama");
        assertThat(onFx(() -> viewModel.adding().get())).isFalse();
    }

    // IF the registry holds no provider, THEN nothing may be edited or current, and the list is empty.
    @Test
    void providers_emptyRegistry_hasNoEditedAndNoCurrentProvider() {
        configs.all().forEach(config -> configs.delete(config.id()));
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        assertThat(onFx(() -> List.copyOf(viewModel.providers()))).isEmpty();
        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEmpty();
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEmpty();
    }

    // IF adding were not a state of its own, THEN a new provider's blank form could not be told from Ollama's.
    @Test
    void startAdding_fromOllama_clearsEditedAndKeepsCurrent() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        onFx(() -> {
            viewModel.startAdding();
            return null;
        });

        assertThat(onFx(() -> viewModel.adding().get())).isTrue();
        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEmpty();
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    // IF a cancelled addition left the blank form up, THEN the screen would show no provider while one is current.
    @Test
    void cancelAdding_afterStartAdding_editsTheCurrentProviderAgain() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());
        onFx(() -> {
            viewModel.startAdding();
            viewModel.cancelAdding();
            return null;
        });

        assertThat(onFx(() -> viewModel.adding().get())).isFalse();
        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("ollama");
    }

    // IF a provider's own name were not shown, THEN a person's "Office LM" would read as its slug.
    @Test
    void providerName_providerWithDisplayName_isThatName() {
        configs.save(new ProviderConfig(
                "office-lm",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://10.0.0.5:1234/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT,
                "Office LM",
                ProviderAuth.NONE,
                null,
                null,
                null,
                null,
                java.util.Map.of(),
                List.of()));
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        assertThat(onFx(() -> viewModel.providerName("office-lm"))).isEqualTo("Office LM");
        assertThat(onFx(() -> viewModel.providerName("ollama"))).isEqualTo("Ollama");
        assertThat(onFx(() -> viewModel.providerName("ghost"))).isEqualTo("ghost");
    }

    // IF the check were offered without a model, THEN it would verify a server against nothing.
    @Test
    void checkAvailable_noModel_isFalse() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        assertThat(onFx(() ->
                        viewModel.tests().available(ProviderTest.INFERENCE).get()))
                .isFalse();
    }

    // IF choosing a model did not make the check available, THEN a person could never run it.
    @Test
    void checkAvailable_afterModelSet_isTrue() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());

        onFx(() -> {
            viewModel.draftModel().set(MODEL);
            return null;
        });

        assertThat(onFx(() ->
                        viewModel.tests().available(ProviderTest.INFERENCE).get()))
                .isTrue();
    }

    // IF a check ran with no model chosen, THEN the verifier would be asked about an empty model id.
    @Test
    void check_noModel_doesNothing() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.idle();
        final SettingsViewModel viewModel = viewModel(verifier);

        onFx(() -> {
            viewModel.tests().run(ProviderTest.INFERENCE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(verifier.callCount()).isZero();
        assertThat(onFx(() -> viewModel.tests().checking().get())).isFalse();
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
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a passing provider test raised a toast, THEN its answer would be told twice, or in the corner behind the
    // dialog it came from: the test reports in its own dialog, and the notifications spec names no toast for it.
    @Test
    void check_allStagesPass_raisesNoToastAndTheChipsAreTheAnswer() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));

        checkWithModel(viewModel);

        assertThat(toasts.raised()).isEmpty();
        assertThat(toasts.feedback()).isEmpty();
        assertThat(stagesOf(viewModel)).extracting(StageChip::status).containsOnly(StageStatus.PASSED);
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

        assertThat(onFx(() -> viewModel.tests().checking().get())).isFalse();
        assertThat(onFx(() ->
                        viewModel.tests().available(ProviderTest.INFERENCE).get()))
                .isTrue();
        assertThat(errors.presented()).extracting(AppError::code).containsExactly(ErrorCode.internal);
        assertThat(toasts.raised()).isEmpty();
    }

    // IF a model typed for one server stayed when another was edited, THEN the check would ask the new server for a
    // model it may not have, and show the old server's verdict beside it.
    @Test
    void editProvider_afterModelTyped_clearsDraftModelAndStages() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));
        checkWithModel(viewModel);

        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });

        assertThat(onFx(() -> viewModel.draftModel().get())).isEmpty();
        assertThat(stagesOf(viewModel)).isEmpty();
        assertThat(onFx(() ->
                        viewModel.tests().available(ProviderTest.INFERENCE).get()))
                .isFalse();
    }

    // IF re-opening the edited provider cleared the draft, THEN clicking the open row would discard typing.
    @Test
    void editProvider_sameProvider_keepsDraftModelAndStages() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));
        checkWithModel(viewModel);

        onFx(() -> {
            viewModel.editProvider("ollama");
            return null;
        });

        assertThat(onFx(() -> viewModel.draftModel().get())).isEqualTo(MODEL);
        assertThat(stagesOf(viewModel)).hasSize(3);
    }

    // IF coming back to the current provider showed an empty model, THEN its saved model would look lost.
    @Test
    void editProvider_backToCurrent_restoresTheCurrentModel() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());
        onFx(() -> {
            viewModel.applySavedCurrentModel("qwen3:8b");
            viewModel.editProvider("lmstudio");
            viewModel.editProvider("ollama");
            return null;
        });

        assertThat(onFx(() -> viewModel.draftModel().get())).isEqualTo("qwen3:8b");
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("qwen3:8b");
    }

    // IF the check were aimed at the current provider while another is being edited, THEN its verdict would be
    // shown on the wrong form.
    @Test
    void check_otherProviderEdited_asksTheVerifierAboutThatProvider() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(allPassed());
        final SettingsViewModel viewModel = viewModel(verifier);
        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });

        checkWithModel(viewModel);

        assertThat(verifier.selections()).containsExactly(new ModelSelection("lmstudio", MODEL));
    }

    // IF leaving the current provider forgot nothing, THEN a deleted provider would stay selected.
    @Test
    void forgetCurrent_afterLoad_clearsProviderAndModel() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());
        onFx(() -> {
            viewModel.applySavedCurrentModel("qwen3:8b");
            viewModel.forgetCurrent();
            return null;
        });

        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEmpty();
        assertThat(onFx(() -> viewModel.model().get())).isEmpty();
        assertThat(onFx(viewModel::selection)).isEmpty();
    }
}
