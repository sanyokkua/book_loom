package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelSelection;

/** What dropping or keeping typing does: Discard with no provider to go back to, and a model adopted from outside. */
class ProviderFormLeaveTest extends ProviderFormTestBase {

    // IF Discard left the form dirty when no provider exists, THEN the leave question would be asked again forever.
    @Test
    void discard_addingWithNoProviders_leavesTheFormClean() {
        configs.delete("ollama");
        configs.delete("lmstudio");
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        onFx(() -> {
            viewModel.startAdding();
            return null;
        });
        edit(form, draft -> draft.withName("Office LM"));

        onFx(() -> {
            form.discard();
            return null;
        });

        assertThat(dirtyOf(form)).isFalse();
        assertThat(draftOf(form).name()).isEmpty();
    }

    // IF a model adopted from outside replaced the open form, THEN the first-launch look would erase unsaved typing.
    @Test
    void adopt_formDirty_keepsTheTypingAndTheOpenProvider() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withRequestSeconds("240"));

        onFx(() -> {
            viewModel.adopt(new ModelSelection("lmstudio", "qwen3:8b"));
            return null;
        });

        assertThat(draftOf(form).requestSeconds()).isEqualTo("240");
        assertThat(onFx(() -> viewModel.editedProviderId().get())).isEqualTo("ollama");
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("lmstudio");
        assertThat(dirtyOf(form)).isTrue();
    }

    // IF adopting the current provider's model marked the form dirty, THEN a person who typed nothing would be asked.
    @Test
    void adopt_cleanFormOnTheCurrentProvider_staysCleanAndShowsTheModel() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);

        onFx(() -> {
            viewModel.adopt(new ModelSelection("ollama", "qwen3:8b"));
            return null;
        });

        assertThat(dirtyOf(form)).isFalse();
        assertThat(draftOf(form).model()).isEqualTo("qwen3:8b");
    }

    // IF a saved name with stray spaces were compared as typed, THEN the form would stay dirty after a good save.
    @Test
    void save_nameWithStraySpaces_endsCleanShowingWhatWasStored() {
        final SettingsViewModel viewModel = viewModel();
        final ProviderForm form = form(viewModel);
        edit(form, draft -> draft.withName("  Office  "));

        save(form);

        assertThat(dirtyOf(form)).isFalse();
        assertThat(draftOf(form).name()).isEqualTo("Office");
    }
}
