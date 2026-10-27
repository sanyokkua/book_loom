package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.ScriptedProviderVerifier;

/** The tests run against the form as it is typed, saved or not, and change nothing. */
class ProviderTestRunnerDraftTest extends ProviderFormTestBase {

    private void run(final SettingsViewModel viewModel, final ProviderTest test) {
        onFx(() -> {
            viewModel.tests().run(test);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static List<StageChip> chips(final SettingsViewModel viewModel) {
        return onFx(() -> List.copyOf(viewModel.tests().stages()));
    }

    private static Optional<TestSubject> subjectOf(final SettingsViewModel viewModel) {
        return onFx(() -> viewModel.testSubject().get());
    }

    // IF a test asked about the saved Office LM while the form held another address, THEN the verdict would be about
    // a server the person is no longer typing.
    @Test
    void run_unsavedOfficeLm_isVerifiedAsADraftAndNothingIsStored() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(allPassed());
        final SettingsViewModel viewModel = viewModel(verifier);
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);
        edit(form, draft -> draft.withModel("qwen3:8b"));

        run(viewModel, ProviderTest.INFERENCE);

        assertThat(verifier.drafts())
                .extracting(
                        call -> call.config().id(),
                        call -> call.config().baseUrl().toString(),
                        ScriptedProviderVerifier.DraftCall::modelId,
                        ScriptedProviderVerifier.DraftCall::policy)
                .containsExactly(tuple("new-provider", "http://10.0.0.5:1234/v1", "qwen3:8b", VerificationPolicy.FULL));
        assertThat(configs.saves()).isZero();
        assertThat(modelSettings.saves()).isEmpty();
    }

    // IF the connection test sent a model, THEN it could fail on a model nobody has chosen yet.
    @Test
    void run_connectionOnAnUnsavedProvider_asksTheConnectionDepthWithNoModel() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(allPassed());
        final SettingsViewModel viewModel = viewModel(verifier);
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);

        run(viewModel, ProviderTest.CONNECTION);

        assertThat(verifier.drafts())
                .extracting(ScriptedProviderVerifier.DraftCall::modelId, ScriptedProviderVerifier.DraftCall::policy)
                .containsExactly(tuple("", VerificationPolicy.CONNECTION));
        assertThat(verifier.connectionCalls()).isEmpty();
    }

    // IF renaming a provider threw away its results, THEN a person could never name it after testing it.
    @Test
    void edit_nameOnly_keepsTheResults() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b"));
        run(viewModel, ProviderTest.INFERENCE);

        edit(form, draft -> draft.withName("My Ollama"));

        assertThat(chips(viewModel)).hasSize(3);
        assertThat(subjectOf(viewModel))
                .hasValueSatisfying(subject -> assertThat(subject.name()).isEqualTo("My Ollama"));
    }

    // IF an address edit kept the results, THEN they would describe a server that is no longer typed.
    @Test
    void edit_address_clearsTheResults() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b"));
        run(viewModel, ProviderTest.INFERENCE);

        edit(form, draft -> draft.withAddress("http://localhost:11500"));

        assertThat(chips(viewModel)).isEmpty();
    }

    // IF a test could start on a draft the rules refuse, THEN it would call a server with an address nobody can read.
    @Test
    void edit_blockingFinding_noTestIsAvailableAndTheScreenIsToldWhy() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());
        final ProviderForm form = form(viewModel);

        edit(form, draft -> draft.withRequestSeconds("many"));

        assertThat(subjectOf(viewModel)).isEmpty();
        assertThat(onFx(() -> viewModel.testsBlocked().get())).isTrue();
        assertThat(onFx(() ->
                        viewModel.tests().available(ProviderTest.CONNECTION).get()))
                .isFalse();
    }

    // IF fixing the field did not lift the block, THEN the person could never test again.
    @Test
    void edit_blockingFindingFixed_testsAreAvailableAgain() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withRequestSeconds("many"));

        edit(form, draft -> draft.withRequestSeconds("180"));

        assertThat(onFx(() -> viewModel.testsBlocked().get())).isFalse();
        assertThat(onFx(() ->
                        viewModel.tests().available(ProviderTest.CONNECTION).get()))
                .isTrue();
    }

    // IF a provider being added were tested under a real id, THEN a speed could be stored for a provider that is not.
    @Test
    void subject_whileAdding_hasTheDraftIdAndIsNotStored() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());
        final ProviderForm form = form(viewModel);

        addOfficeLm(viewModel, form);

        assertThat(subjectOf(viewModel)).hasValueSatisfying(subject -> {
            assertThat(subject.config().id()).isEqualTo(TestSubject.DRAFT_ID);
            assertThat(subject.stored()).isFalse();
            assertThat(subject.name()).isEqualTo("Office LM");
        });
    }

    // IF an open saved provider were tested under another id, THEN its speed would be kept for the wrong provider.
    @Test
    void subject_storedProvider_hasItsOwnIdAndIsStored() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.idle());
        form(viewModel);

        assertThat(subjectOf(viewModel)).hasValueSatisfying(subject -> {
            assertThat(subject.config().id()).isEqualTo("ollama");
            assertThat(subject.stored()).isTrue();
        });
    }

    // IF the inference test did not register as model work, THEN a run could start beside it.
    @Test
    void run_inferenceInFlight_registersTheInferenceActivity() throws InterruptedException {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.gated(allPassed());
        useCountingPool();
        final SettingsViewModel viewModel = viewModel(verifier);
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b"));

        onFx(() -> {
            viewModel.tests().run(ProviderTest.INFERENCE);
            return null;
        });
        verifier.awaitEntered();

        assertThat(onFx(() -> activities.running().stream().map(Activity::kind).toList()))
                .containsExactly(ActivityKind.PROVIDER_INFERENCE_TEST);
        verifier.release();
    }

    // IF a connection report carried no stage for the later two, THEN they would be missing instead of not attempted.
    @Test
    void run_connectionOnly_showsOnlyTheConnectionFinding() {
        final SettingsViewModel viewModel = viewModel(ScriptedProviderVerifier.returning(allPassed()));
        form(viewModel);

        run(viewModel, ProviderTest.CONNECTION);

        assertThat(chips(viewModel))
                .extracting(StageChip::stage, StageChip::status)
                .containsExactly(tuple(VerificationStage.CONNECTION, StageStatus.PASSED));
    }
}
