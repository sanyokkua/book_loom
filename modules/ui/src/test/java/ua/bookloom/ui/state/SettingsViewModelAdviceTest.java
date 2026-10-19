package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.state.ModelAdvice.Level;
import ua.bookloom.ui.state.ModelAdvice.Note;

/** The advice the settings view model publishes follows the selected provider and the chosen model. */
class SettingsViewModelAdviceTest extends SettingsViewModelTestBase {

    private static List<Level> levels(final SettingsViewModel viewModel) {
        return onFx(() -> viewModel.modelNotes().get().stream().map(Note::level).toList());
    }

    private static void choose(final SettingsViewModel viewModel, final String model) {
        onFx(() -> {
            viewModel.setModelText(model);
            return null;
        });
    }

    @Test
    void modelNotes_noModelChosenOnOllama_isEmpty() {
        assertThat(levels(viewModel())).isEmpty();
    }

    // IF the advice did not follow the typed model, THEN the note would describe the model chosen before it.
    @Test
    void modelNotes_modelTypedThenChanged_followsEachText() {
        final SettingsViewModel viewModel = viewModel();

        choose(viewModel, "gemma4:26b-mlx");
        assertThat(levels(viewModel)).containsExactly(Level.RECOMMENDED);

        choose(viewModel, "gemma4:e2b-mlx");
        assertThat(levels(viewModel)).containsExactly(Level.WARNING);

        choose(viewModel, "gemma3:12b");
        assertThat(levels(viewModel)).isEmpty();
    }

    // IF the server's note waited for a model, THEN a person who has not chosen one yet would not be told that LM
    // Studio
    // does not stream.
    @Test
    void modelNotes_lmStudioSelected_hasTheServerNoteEvenWithoutAModel() {
        final SettingsViewModel viewModel = viewModel();

        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            return null;
        });

        assertThat(levels(viewModel)).containsExactly(Level.INFO);
    }

    @Test
    void modelNotes_lmStudioWithGptOss_hasTheModelNoteThenTheServerNote() {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            return null;
        });

        choose(viewModel, "openai/gpt-oss-20b");

        assertThat(levels(viewModel)).containsExactly(Level.WARNING, Level.INFO);
    }

    // IF a provider change kept the old notes, THEN Ollama's model advice would stay on LM Studio's empty entry.
    @Test
    void modelNotes_providerChangedBackToOllama_dropsTheServerAndModelNotes() {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            return null;
        });
        choose(viewModel, "google/gemma-4-26b");

        onFx(() -> {
            viewModel.selectProvider("ollama");
            return null;
        });

        assertThat(levels(viewModel)).isEmpty();
    }
}
