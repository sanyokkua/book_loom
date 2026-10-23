package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.ScriptedProviderVerifier;

/** Use this one: the open provider and its typed model become the current ones, and nothing else is saved. */
class ProviderFormUseThisTest extends ProviderFormTestBase {

    private SettingsViewModel openLmStudio() {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });
        return viewModel;
    }

    private static boolean useThis(final ProviderForm form) {
        final List<String> ran = new ArrayList<>();
        onFx(() -> {
            form.useThis(() -> ran.add("then"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        return !ran.isEmpty();
    }

    private static boolean canUse(final ProviderForm form) {
        return onFx(() -> form.canUseThis().get());
    }

    // IF Use this one did not write the choice, THEN the next start would open on the old provider.
    @Test
    void useThis_otherProviderWithModel_writesTheCurrentModelAndAdoptsIt() {
        final SettingsViewModel viewModel = openLmStudio();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel("qwen3:8b"));

        final boolean ran = useThis(form);

        assertThat(ran).isTrue();
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("lmstudio", "qwen3:8b"));
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("lmstudio");
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("qwen3:8b");
        assertThat(dirtyOf(form)).isFalse();
    }

    // IF a blank model refused the switch, THEN a person could not move to a provider before picking its model.
    @Test
    void useThis_blankModel_writesTheProviderWithNoModel() {
        final SettingsViewModel viewModel = openLmStudio();
        final ProviderForm form = form(viewModel);

        useThis(form);

        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("lmstudio", ""));
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("lmstudio");
    }

    // IF a padded model were stored as typed, THEN every later comparison with the model's real name would miss.
    @Test
    void useThis_modelTypedWithSpaces_isStoredStrippedAndTheFormIsClean() {
        final SettingsViewModel viewModel = openLmStudio();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withModel(" qwen3:8b "));

        useThis(form);

        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(new ModelSelection("lmstudio", "qwen3:8b"));
        assertThat(dirtyOf(form)).isFalse();
    }

    // IF an address change were carried along, THEN Use this one would silently save more than it says.
    @Test
    void canUseThis_onlyTheModelTyped_isAllowedButAnAddressEditIsNot() {
        final SettingsViewModel viewModel = openLmStudio();
        final ProviderForm form = form(viewModel);
        assertThat(canUse(form)).isTrue();

        edit(form, draft -> draft.withModel("qwen3:8b"));
        assertThat(canUse(form)).isTrue();

        edit(form, draft -> draft.withAddress("http://localhost:1235/v1"));
        assertThat(canUse(form)).isFalse();
        assertThat(useThis(form)).isFalse();
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).isEmpty();
    }

    // IF a provider that is not saved could be made current, THEN the current model would name a provider nobody has.
    @Test
    void canUseThis_whileAdding_isNotAllowed() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);

        addOfficeLm(viewModel, form);

        assertThat(canUse(form)).isFalse();
    }

    // IF a refused write still moved the choice, THEN the window and the next start would disagree about the model.
    @Test
    void useThis_storeRefuses_changesNothingAndSaysSo() {
        final RefusingSettings refusing = new RefusingSettings();
        final SettingsViewModel viewModel = onFx(() -> new SettingsViewModel(
                configs,
                ScriptedProviderVerifier.idle(),
                newModelListing(),
                errors,
                executor,
                activities,
                refusing,
                EnglishMessages.create(),
                new ModelSpeedRecorder(modelSettings, speeds, executor)));
        WaitForAsyncUtils.waitForFxEvents();
        final ProviderForm form = onFx(() ->
                new ProviderForm(viewModel, configs, modelSettings, refusing, name -> false, technical, executor));
        onFx(() -> {
            viewModel.editProvider("lmstudio");
            return null;
        });

        final boolean ran = useThis(form);

        assertThat(ran).isFalse();
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
        assertThat(onFx(() -> form.saveFailed().get())).isTrue();
        assertThat(onFx(() -> form.saving().get())).isFalse();
    }

    /** A store whose writes are refused. */
    private static final class RefusingSettings implements SettingsRepository {

        @Override
        public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
            return Result.ok(Optional.empty());
        }

        @Override
        public <T> Result<T> put(final AppSetting<T> setting, final T value) {
            return Result.err(AppError.of(ErrorCode.internal, "Not stored", "The store refused."));
        }

        @Override
        public Result<Boolean> remove(final AppSetting<?> setting) {
            return Result.err(AppError.of(ErrorCode.internal, "Not stored", "The store refused."));
        }

        @Override
        public Result<Map<String, String>> all() {
            return Result.ok(Map.of());
        }
    }
}
