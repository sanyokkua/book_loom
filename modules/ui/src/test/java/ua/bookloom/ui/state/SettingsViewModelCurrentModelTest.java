package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.ScriptedProviderVerifier;

/** The model chosen last time is read when Settings loads, and the first-launch look can adopt another one. */
class SettingsViewModelCurrentModelTest extends SettingsViewModelTestBase {

    private SettingsViewModel build() {
        final SettingsViewModel built = onFx(() -> new SettingsViewModel(
                configs,
                ScriptedProviderVerifier.idle(),
                newModelListing(),
                errors,
                executor,
                activities,
                settings,
                EnglishMessages.create(),
                new ModelSpeedRecorder(new ScriptedModelSettings(), new ModelSpeeds(), new DirectExecutor())));
        WaitForAsyncUtils.waitForFxEvents();
        return built;
    }

    // IF the stored model were not applied after the providers load, THEN every launch would forget the chosen model.
    @Test
    void constructor_storedCurrentModel_isSelectedOnceTheProvidersAreListed() {
        settings.put(AppSetting.CURRENT_MODEL, new ModelSelection("lmstudio", "qwen3:8b"));

        final SettingsViewModel viewModel = build();

        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("lmstudio");
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("qwen3:8b");
        assertThat(onFx(viewModel::selection)).contains(new ModelSelection("lmstudio", "qwen3:8b"));
    }

    // IF a model of a provider that is no longer listed were applied, THEN the selection would name a provider with no
    // row.
    @Test
    void constructor_storedModelOfAnUnlistedProvider_leavesTheSelectionUnset() {
        settings.put(AppSetting.CURRENT_MODEL, new ModelSelection("ghost", "qwen3:8b"));

        final SettingsViewModel viewModel = build();

        assertThat(onFx(() -> viewModel.model().get())).isEmpty();
        assertThat(onFx(viewModel::selection)).isEmpty();
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    @Test
    void constructor_noStoredModel_leavesTheModelEmpty() {
        final SettingsViewModel viewModel = build();

        assertThat(onFx(() -> viewModel.model().get())).isEmpty();
        assertThat(onFx(viewModel::selection)).isEmpty();
    }

    @Test
    void adopt_listedProvider_setsTheProviderAndTheModel() {
        final SettingsViewModel viewModel = build();

        onFx(() -> {
            viewModel.adopt(new ModelSelection("lmstudio", "qwen3:8b"));
            return null;
        });

        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("lmstudio");
        assertThat(onFx(viewModel::selection)).contains(new ModelSelection("lmstudio", "qwen3:8b"));
    }

    @Test
    void adopt_unlistedProvider_changesNothing() {
        final SettingsViewModel viewModel = build();

        onFx(() -> {
            viewModel.adopt(new ModelSelection("ghost", "qwen3:8b"));
            return null;
        });

        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
        assertThat(onFx(viewModel::selection)).isEmpty();
    }

    // IF the rows were not read again, THEN a program the look just saved would be missing from Settings.
    @Test
    void reloadProviders_registryGainedAProvider_listsItAndAdoptCanSelectIt() {
        final SettingsViewModel viewModel = build();
        configs.save(new ProviderConfig(
                "other",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://127.0.0.1:5000/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));

        onFx(() -> {
            viewModel.reloadProviders();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() ->
                        viewModel.providers().stream().map(ProviderRow::id).toList()))
                .contains("other");
    }
}
