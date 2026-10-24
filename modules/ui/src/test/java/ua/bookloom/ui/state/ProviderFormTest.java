package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedProviderVerifier;
import ua.bookloom.ui.i18n.MessageKey;

/** The Providers form: what it holds while a person types and what saving and cancelling do. */
class ProviderFormTest extends ProviderFormTestBase {
    // IF a new provider were not stored once and listed with its host, THEN Add would look like it did nothing.
    @Test
    void save_newOfficeLm_isStoredOnceAndListedWithItsHost() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);

        final boolean ran = save(form);

        assertThat(ran).isTrue();
        assertThat(configs.saves()).isEqualTo(1);
        assertThat(onFx(() -> viewModel.providers().stream()
                        .map(ProviderRow::hostPort)
                        .toList()))
                .contains("10.0.0.5:1234");
        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("office-lm");
        assertThat(onFx(() -> viewModel.adding().get())).isFalse();
        assertThat(dirtyOf(form)).isFalse();
    }

    // IF a timeout edit did not make the form dirty while the store still held the old value, THEN leaving would
    // lose it silently; IF Save did not clean it, THEN the question would come back.
    @Test
    void edit_timeoutChanged_isDirtyWhileStoreHoldsOldThenSaveCleans() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        assertThat(dirtyOf(form)).isFalse();

        edit(form, draft -> draft.withRequestSeconds("240"));

        assertThat(dirtyOf(form)).isTrue();
        assertThat(configs.find("ollama").orElseThrow().requestTimeout().toSeconds())
                .isEqualTo(180);
        save(form);
        assertThat(configs.find("ollama").orElseThrow().requestTimeout().toSeconds())
                .isEqualTo(240);
        assertThat(dirtyOf(form)).isFalse();
    }

    // IF a key written as a header value were saved, THEN a secret would be stored in the database.
    @Test
    void save_headerValueLooksLikeAKey_isRefusedNothingStoredAndThenNotRun() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);
        edit(form, draft -> draft.withHeaders(List.of(new ProviderDraft.HeaderRow("X-Api-Key", "sk-1"))));

        final boolean ran = save(form);

        assertThat(ran).isFalse();
        assertThat(configs.saves()).isZero();
        assertThat(onFx(() -> form.errorsShown().get())).isTrue();
        assertThat(onFx(() -> form.findings().get())).anySatisfy(finding -> {
            assertThat(finding.key()).isEqualTo(MessageKey.SETTINGS_ERROR_HEADER_LITERAL);
            assertThat(finding.args()).containsExactly("X-Api-Key");
        });
    }

    // IF two providers could share a name, THEN the list and the title bar could not tell them apart.
    @Test
    void save_nameAnotherProviderHasInOtherCase_isRefusedWithAFindingOnTheName() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);
        edit(form, draft -> draft.withName(" OLLAMA "));

        final boolean ran = save(form);

        assertThat(ran).isFalse();
        assertThat(configs.saves()).isZero();
        assertThat(onFx(() -> form.findings().get()))
                .extracting(ProviderDraft.FormFinding::key)
                .contains(MessageKey.SETTINGS_ERROR_NAME_USED);
    }

    // IF an environment reference were rewritten on the way, THEN the key would never be found at call time.
    @Test
    void save_environmentReferenceHeader_isStoredVerbatim() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);
        edit(form, draft -> draft.withHeaders(List.of(new ProviderDraft.HeaderRow("X-Api-Key", "${env:OFFICE_KEY}"))));

        save(form);

        assertThat(configs.find("office-lm").orElseThrow().headers()).containsEntry("X-Api-Key", "${env:OFFICE_KEY}");
    }

    // IF saving the current provider did not store its Model field, THEN the typed model would never be used.
    @Test
    void save_currentProviderWithModel_writesTheCurrentModel() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        onFx(() -> {
            viewModel.draftModel().set("qwen3:8b");
            return null;
        });
        assertThat(dirtyOf(form)).isTrue();

        save(form);

        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("ollama", "qwen3:8b"));
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("qwen3:8b");
        assertThat(dirtyOf(form)).isFalse();
    }

    // IF a blank Model on the current provider kept the old model, THEN a person could never clear it.
    @Test
    void save_currentProviderBlankModel_removesTheCurrentModel() {
        settings.put(AppSetting.CURRENT_MODEL, new ModelSelection("ollama", "qwen3:8b"));
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        onFx(() -> {
            viewModel.draftModel().set("");
            return null;
        });

        save(form);

        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).isEmpty();
        assertThat(onFx(() -> viewModel.model().get())).isEmpty();
    }

    // IF saving another provider moved the current model, THEN Save on a side form would switch what translates.
    @Test
    void save_otherProvider_leavesTheCurrentModelAlone() {
        settings.put(AppSetting.CURRENT_MODEL, new ModelSelection("ollama", "qwen3:8b"));
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });
        edit(form, draft -> draft.withRequestSeconds("300").withModel("other"));

        save(form);

        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("ollama", "qwen3:8b"));
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("qwen3:8b");
    }

    // IF Cancel left the typing in place, THEN the form would still claim changes the store never saw.
    @Test
    void discard_afterEdit_restoresTheSavedValuesAndIsClean() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withRequestSeconds("240").withModel("typed"));

        onFx(() -> {
            form.discard();
            return null;
        });

        assertThat(draftOf(form).requestSeconds()).isEqualTo("180");
        assertThat(onFx(() -> viewModel.draftModel().get())).isEmpty();
        assertThat(dirtyOf(form)).isFalse();
    }

    // IF cancelling an addition stayed on the blank form, THEN the screen would show no provider.
    @Test
    void discard_whileAdding_goesBackToTheCurrentProvider() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);

        onFx(() -> {
            form.discard();
            return null;
        });

        assertThat(onFx(() -> viewModel.adding().get())).isFalse();
        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("ollama");
        assertThat(draftOf(form).name()).isEqualTo("Ollama");
        assertThat(dirtyOf(form)).isFalse();
    }

    // IF deleting a provider left its per-model settings behind, THEN a later provider with the same id would inherit
    // IF deleting the current provider kept the stored current model, THEN every run would name a provider that is
    // IF the form held the variable's value, THEN a key could leak into the window; it holds only whether it is set.
    @Test
    void credentialStatus_variableSetOrNot_isReportedWithoutItsValue() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        assertThat(onFx(() -> form.credentialStatus().get())).isEqualTo(ProviderDraft.CredentialStatus.NONE);

        edit(form, draft -> draft.withAuth(ProviderAuth.BEARER).withCredentialRef(OFFICE_KEY));
        assertThat(onFx(() -> form.credentialStatus().get())).isEqualTo(ProviderDraft.CredentialStatus.SET);

        edit(form, draft -> draft.withCredentialRef("OTHER_KEY"));
        assertThat(onFx(() -> form.credentialStatus().get())).isEqualTo(ProviderDraft.CredentialStatus.NOT_SET);
    }

    // IF Ollama accepted a chat path, THEN its own protocol would be sent to a path it does not serve.
    @Test
    void save_ollamaWithChatPath_isRefusedAndOpenAiModelsPathIsAccepted() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withChatPath("/chat"));
        final boolean ollamaSaved = save(form);
        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });
        edit(form, draft -> draft.withModelsPath("/models"));
        final boolean lmSaved = save(form);

        assertThat(ollamaSaved).isFalse();
        assertThat(lmSaved).isTrue();
        assertThat(configs.find("lmstudio").orElseThrow().modelsPath()).isEqualTo("/models");
    }

    // IF the connection details stayed folded while there is something to fix or fill in, THEN the person would not
    // find it.
    @Test
    void foldOpen_healthyThenAddingThenCancelled_followsTheRule() {
        final SettingsViewModel viewModel = viewModel(
                ScriptedProviderVerifier.returning(report(outcome(VerificationStage.CONNECTION, StageStatus.FAILED))));
        final ProviderForm form = form(viewModel);
        assertThat(onFx(() -> form.foldOpen().get())).isFalse();

        onFx(() -> {
            viewModel.startAdding();
            return null;
        });
        assertThat(onFx(() -> form.foldOpen().get())).isTrue();

        onFx(() -> {
            viewModel.cancelAdding();
            return null;
        });
        assertThat(onFx(() -> form.foldOpen().get())).isFalse();
    }

    // IF a failed check left the details folded, THEN the person would not see the address to fix.
    @Test
    void foldOpen_failedCheckThenOtherProviderThenTechnicalDetails_followsTheRule() {
        final SettingsViewModel viewModel = viewModel(
                ScriptedProviderVerifier.returning(report(outcome(VerificationStage.CONNECTION, StageStatus.FAILED))));
        final ProviderForm form = form(viewModel);
        onFx(() -> {
            viewModel.tests().run(ProviderTest.CONNECTION);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(onFx(() -> form.foldOpen().get())).isTrue();

        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });
        assertThat(onFx(() -> form.foldOpen().get())).isFalse();
        onFx(() -> {
            technical.property().set(true);
            return null;
        });
        assertThat(onFx(() -> form.foldOpen().get())).isTrue();
    }

    // IF Change model could not open the fold, THEN the Model field would be hidden when asked for.
    @Test
    void openFoldAt_model_opensTheFoldAndAsksForTheModelField() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);

        onFx(() -> {
            form.openFoldAt(ProviderForm.Spot.MODEL);
            return null;
        });

        assertThat(onFx(() -> form.foldOpen().get())).isTrue();
        assertThat(onFx(() -> form.focusRequest().get())).isEqualTo(ProviderForm.Spot.MODEL);
    }

    // IF a custom model were not offered beside the listed ones, THEN a model the server does not list could not
    // be picked from the list.
    @Test
    void modelChoices_customModels_followTheListedOnesWithoutDuplicates() {
        catalog = ScriptedModelCatalog.returning("gemma3:12b", "llama3");
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withCustomModels(List.of("mine", "gemma3:12b")));
        refreshAndAwait(viewModel);

        assertThat(onFx(form::modelChoices)).containsExactly("gemma3:12b", "llama3", "mine");
    }

    // The draft of the Ollama preset starts from its stored values, so a new probe finds the same shape.
    @Test
    void draft_afterLoad_isTheStoredProviderWithItsNameAndModel() {
        settings.put(AppSetting.CURRENT_MODEL, new ModelSelection("ollama", "qwen3:8b"));
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);

        final ProviderConfig stored = configs.find("ollama").orElseThrow();
        assertThat(draftOf(form)).isEqualTo(ProviderDraft.of(stored, "qwen3:8b").withName("Ollama"));
        assertThat(Set.copyOf(draftOf(form).customModels())).isEmpty();
    }
}
