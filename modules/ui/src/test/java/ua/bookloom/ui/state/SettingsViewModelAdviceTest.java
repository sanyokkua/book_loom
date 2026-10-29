package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
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
            viewModel.editProvider("lmstudio");
            return null;
        });

        assertThat(levels(viewModel)).containsExactly(Level.INFO);
    }

    @Test
    void modelNotes_lmStudioWithGptOss_hasTheModelNoteThenTheServerNote() {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.editProvider("lmstudio");
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
            viewModel.editProvider("lmstudio");
            return null;
        });
        choose(viewModel, "google/gemma-4-26b");

        onFx(() -> {
            viewModel.editProvider("ollama");
            return null;
        });

        assertThat(levels(viewModel)).isEmpty();
    }

    private void store(final String id, final String address, final ServerEngine engine) {
        configs.save(new ProviderConfig(
                        id,
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(address),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(60))
                .withEngine(engine));
    }

    // IF LM Studio were told by its port, THEN a second engine detection would disagree with the stored one: LM Studio
    // on another port would get no note and a generic server on LM Studio's port would get LM Studio's.
    @ParameterizedTest
    @CsvSource({"studio, http://127.0.0.1:5555/v1, LM_STUDIO, true", "office, http://127.0.0.1:1234/v1, GENERIC, false"
    })
    void modelNotes_lmStudioEngineOnAnotherPort_readsTheStoredEngine(
            final String id, final String address, final ServerEngine engine, final boolean lmStudioNote) {
        store(id, address, engine);
        final SettingsViewModel viewModel = viewModel();

        onFx(() -> {
            viewModel.editProvider(id);
            return null;
        });

        assertThat(levels(viewModel).contains(Level.INFO)).isEqualTo(lmStudioNote);
    }
}
