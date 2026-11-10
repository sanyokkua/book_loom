package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelNameFilterTest {

    private static final List<String> IDS = List.of("gemma4:e4b", "gemma4:26b", "qwen3.5:9b");

    private static List<String> narrow(final String typed) {
        return ModelNameFilter.narrow(IDS, id -> id, id -> id, typed);
    }

    @Test
    void narrow_upperCaseGemma4_keepsTheTwoGemmaModels() {
        assertThat(narrow("GEMMA4")).containsExactly("gemma4:e4b", "gemma4:26b");
    }

    @Test
    void narrow_llama_keepsNone() {
        assertThat(narrow("llama")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void narrow_blank_keepsAll(final String typed) {
        assertThat(narrow(typed)).hasSize(3);
    }

    @Test
    void matches_displayNameOnly_matches() {
        assertThat(ModelNameFilter.matches("m1", "Mistral Small (Ollama)", "ollama"))
                .isTrue();
    }

    @Test
    void matches_accentedDisplay_foldsAccents() {
        assertThat(ModelNameFilter.matches("m1", "Modèle café", "cafe")).isTrue();
    }

    @Test
    void of_typedText_testsIds() {
        assertThat(ModelNameFilter.of("QWEN").test("qwen3.5:9b")).isTrue();
        assertThat(ModelNameFilter.of("QWEN").test("gemma4:e4b")).isFalse();
    }
}
