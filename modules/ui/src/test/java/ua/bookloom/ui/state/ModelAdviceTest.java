package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.state.ModelAdvice.Level;
import ua.bookloom.ui.state.ModelAdvice.Note;
import ua.bookloom.ui.state.ModelAdvice.Server;

/** What the Providers tab says about a model and its server, decided from the model's name and the provider kind. */
class ModelAdviceTest {

    private static List<Level> levels(final Server kind, final String model) {
        return ModelAdvice.notesFor(kind, model).stream().map(Note::level).toList();
    }

    // IF a measured model were not named, THEN a person would pick by guess and meet slow, flagged runs.
    @ParameterizedTest
    @CsvSource({
        "OLLAMA, gemma4:26b-mlx, RECOMMENDED",
        "OLLAMA, gemma4:26b, RECOMMENDED",
        "OLLAMA, Gemma4:26B-MLX, RECOMMENDED",
        "OLLAMA, gemma4:e4b-mlx, MINIMUM",
        "OLLAMA, gemma4:e4b, MINIMUM",
        "OLLAMA, gemma4:e2b-mlx, WARNING",
        "OLLAMA, gemma4:e2b, WARNING",
        "OLLAMA, gpt-oss:20b, WARNING",
        "OLLAMA, gpt-oss:120b, WARNING",
        "OLLAMA, '  gemma4:26b-mlx  ', RECOMMENDED",
    })
    void notesFor_ollamaModel_hasOneNoteOfTheMatchingLevel(
            final Server kind, final String model, final Level expected) {
        assertThat(levels(kind, model)).containsExactly(expected);
    }

    // IF a model nobody measured got advice, THEN the note would claim knowledge the app does not have.
    @ParameterizedTest
    @CsvSource({
        "OLLAMA, gemma3:12b",
        "OLLAMA, qwen3:8b",
        "OLLAMA, mistral-small:24b",
        "OLLAMA, gemma4:31b",
        "OLLAMA, ''",
    })
    void notesFor_unknownOrBlankModelOnOllama_hasNoNote(final Server kind, final String model) {
        assertThat(ModelAdvice.notesFor(kind, model)).isEmpty();
    }

    // IF LM Studio's limits were not stated, THEN a slow, unstreamed run would look like a hang.
    @ParameterizedTest
    @CsvSource({
        "LM_STUDIO, some-model",
        "LM_STUDIO, ''",
    })
    void notesFor_lmStudioWithAnUnknownModel_hasTheServerNoteOnly(final Server kind, final String model) {
        assertThat(levels(kind, model)).containsExactly(Level.INFO);
    }

    // IF a generic OpenAI-compatible server got LM Studio's note, THEN it would be told something measured elsewhere.
    @Test
    void notesFor_otherOpenAiCompatibleServer_hasNoServerNote() {
        assertThat(ModelAdvice.notesFor(Server.OTHER_OPENAI_COMPATIBLE, "some-model"))
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "OLLAMA, localhost:11434, ollama, Ollama, OLLAMA",
        "OPENAI_COMPATIBLE, 127.0.0.1:1234, p-1, '', LM_STUDIO",
        "OPENAI_COMPATIBLE, studio.local:8080, lmstudio, '', LM_STUDIO",
        "OPENAI_COMPATIBLE, studio.local:8080, p-2, LM Studio (desk), LM_STUDIO",
        "OPENAI_COMPATIBLE, api.example.com, p-3, QA Test, OTHER_OPENAI_COMPATIBLE",
        "OPENAI_COMPATIBLE, localhost:12345, p-4, '', OTHER_OPENAI_COMPATIBLE",
    })
    void serverOf_row_isToldByKindPortAndName(
            final ProviderKind kind, final String hostPort, final String id, final String name, final Server expected) {
        assertThat(Server.of(new ProviderRow(id, kind, "http://" + hostPort + "/v1", hostPort, name)))
                .isEqualTo(expected);
    }

    // The model's own note comes first, then the server's.
    @ParameterizedTest
    @CsvSource({
        "google/gemma-4-26b, RECOMMENDED",
        "google/gemma-4-e4b, MINIMUM",
        "google/gemma-4-e2b, WARNING",
        "openai/gpt-oss-20b, WARNING",
    })
    void notesFor_lmStudioWithAKnownModel_hasTheModelNoteThenTheServerNote(final String model, final Level first) {
        assertThat(levels(Server.LM_STUDIO, model)).containsExactly(first, Level.INFO);
    }

    @Test
    void notesFor_ollamaModelsOfDifferentKinds_pointAtDifferentWording() {
        final Note recommended =
                ModelAdvice.notesFor(Server.OLLAMA, "gemma4:26b-mlx").get(0);
        final Note minimum =
                ModelAdvice.notesFor(Server.OLLAMA, "gemma4:e4b-mlx").get(0);
        final Note tiny = ModelAdvice.notesFor(Server.OLLAMA, "gemma4:e2b-mlx").get(0);
        final Note reasoning =
                ModelAdvice.notesFor(Server.OLLAMA, "gpt-oss:20b").get(0);

        assertThat(List.of(recommended.text(), minimum.text(), tiny.text(), reasoning.text()))
                .doesNotHaveDuplicates();
    }
}
