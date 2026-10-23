package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ViewNames;

/** The Providers form as the leave guard sees it. */
class ProviderFormDirtySourceTest extends SettingsViewModelTestBase {

    private ProviderFormDirtySource source() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = onFx(() -> new ProviderForm(
                viewModel,
                configs,
                new ua.bookloom.ui.ScriptedModelSettings(),
                settings,
                name -> false,
                new TechnicalDetails(new MemorySettings(), Runnable::run, Runnable::run),
                executor));
        return new ProviderFormDirtySource(form);
    }

    // IF the guard did not see the Providers form, THEN leaving Settings would lose its typing unasked.
    @Test
    void isDirty_untouchedForm_isFalseOnTheSettingsScreen() {
        final ProviderFormDirtySource source = source();

        assertThat(source.home()).isEqualTo(ViewNames.SETTINGS);
        assertThat(onFx(source::isDirty)).isFalse();
    }

    // IF the question named a provider that has no name yet, THEN it would read "Save the changes to ?".
    @Test
    void subject_providerWithAName_isThatNameAndBlankIsNull() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = onFx(() -> new ProviderForm(
                viewModel,
                configs,
                new ua.bookloom.ui.ScriptedModelSettings(),
                settings,
                name -> false,
                new TechnicalDetails(new MemorySettings(), Runnable::run, Runnable::run),
                executor));
        final ProviderFormDirtySource source = new ProviderFormDirtySource(form);

        assertThat(source.subject()).isEqualTo("Ollama");
        onFx(() -> {
            form.edit(draft -> draft.withName(" "));
            return null;
        });
        assertThat(source.subject()).isNull();
        assertThat(onFx(source::isDirty)).isTrue();
    }
}
