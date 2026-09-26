package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ModelSelection;

/** The provider-and-model pair a run is started with, as the settings screen holds it. */
class SettingsViewModelSelectionTest extends SettingsViewModelTestBase {

    // IF the pair were read from anywhere but the chosen provider and model, THEN a run would use another model.
    @Test
    void selection_modelChosen_isTheSelectedProviderAndThatModel() {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.model().set(MODEL);
            return null;
        });

        assertThat(onFx(viewModel::selection)).isEqualTo(Optional.of(new ModelSelection("ollama", MODEL)));
    }

    // IF stray whitespace reached the factory, THEN a model typed with a trailing space would not be found.
    @Test
    void selection_modelWithSurroundingWhitespace_isStripped() {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.model().set("  " + MODEL + " ");
            return null;
        });

        assertThat(onFx(viewModel::selection)).isEqualTo(Optional.of(new ModelSelection("ollama", MODEL)));
    }

    // IF a blank model counted as chosen, THEN a run would start with a model no server has.
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void selection_noModelOrBlank_isEmpty(final String text) {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.model().set(text);
            return null;
        });

        assertThat(onFx(viewModel::selection)).isEmpty();
    }
}
