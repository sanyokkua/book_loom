package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.settings.AppSetting;

/** The Providers form's storage: what a failed save, a delete and the executor do. */
class ProviderFormStorageTest extends ProviderFormTestBase {

    // IF a delete left the provider's per-model settings behind, THEN a provider added later under the same id would
    // inherit them.
    @Test
    void delete_nonCurrentProvider_removesTheRowAndItsModelSettings() {
        modelSettings.save(ModelSettings.defaults("lmstudio", "m1"));
        modelSettings.save(ModelSettings.defaults("ollama", "m2"));
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        final List<String> ran = new ArrayList<>();

        onFx(() -> {
            form.delete("lmstudio", () -> ran.add("then"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() ->
                        viewModel.providers().stream().map(ProviderRow::id).toList()))
                .containsExactly("ollama");
        assertThat(modelSettings.all().data())
                .extracting(ModelSettings::providerId)
                .containsExactly("ollama");
        assertThat(ran).containsExactly("then");
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    // IF deleting the current provider left a current model or a first-listed stand-in, THEN runs would silently use an
    // unchosen provider.
    @Test
    void delete_currentProvider_removesTheCurrentModelAndClearsTheSelection() {
        settings.put(AppSetting.CURRENT_MODEL, new ModelSelection("ollama", "qwen3:8b"));
        modelSettings.save(ModelSettings.defaults("ollama", "qwen3:8b"));
        modelSettings.save(ModelSettings.defaults("lmstudio", "m1"));
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);

        onFx(() -> {
            form.delete("ollama", () -> {});
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).isEmpty();
        assertThat(onFx(() -> viewModel.model().get())).isEmpty();
        assertThat(onFx(viewModel::selection)).isEmpty();
        assertThat(configs.find("ollama")).isEmpty();
        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("lmstudio");
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEmpty();
        assertThat(modelSettings.all().data())
                .extracting(ModelSettings::providerId)
                .containsExactly("lmstudio");
    }

    // IF a failed read of the per-model settings were reported as a clean delete, THEN stale rows would stay unseen.
    @Test
    void delete_modelSettingsCannotBeRead_reportsTheFailureAndDoesNotRunThen() {
        modelSettings.failListing();
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        final List<String> ran = new ArrayList<>();

        onFx(() -> {
            form.delete("lmstudio", () -> ran.add("then"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> form.saveFailed().get())).isTrue();
        assertThat(onFx(() -> form.saving().get())).isFalse();
        assertThat(ran).isEmpty();
    }

    // IF a store failure dropped the typing, THEN the person would have to type it all again.
    @Test
    void save_storeFails_keepsTheDraftFlagsTheFailureAndDoesNotRunThen() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withRequestSeconds("240"));
        configs.failSaves();

        final boolean ran = save(form);

        assertThat(ran).isFalse();
        assertThat(onFx(() -> form.saveFailed().get())).isTrue();
        assertThat(draftOf(form).requestSeconds()).isEqualTo("240");
        assertThat(dirtyOf(form)).isTrue();
        assertThat(onFx(() -> form.saving().get())).isFalse();
    }

    // IF a storage call were made on the FX thread, THEN the window would freeze behind the disk.
    @Test
    void saveAndDelete_withARealPool_makeNoStorageCallOnTheFxThread() throws InterruptedException, TimeoutException {
        final CountingPool pool = useCountingPool();
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        addOfficeLm(viewModel, form);

        onFx(() -> {
            form.save(() -> {});
            return null;
        });
        awaitIdle(form);
        onFx(() -> {
            form.delete("office-lm", () -> {});
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> configs.find("office-lm").isEmpty());
        pool.awaitFinished(4);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(configs.fxThreadCalls()).isZero();
        assertThat(settings.fxThreadCalls()).isZero();
        assertThat(modelSettings.fxThreadCalls()).isZero();
    }

    private static void awaitIdle(final ProviderForm form) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> !onFx(() -> form.saving().get()));
        WaitForAsyncUtils.waitForFxEvents();
    }
}
