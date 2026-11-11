package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;

class ModelRecommendationTest {

    private static final long GB = 1L << 30;

    @ParameterizedTest
    @CsvSource({"8, gemma4:e4b", "23, gemma4:e4b", "24, gemma4:26b-a4b", "35, gemma4:26b-a4b", "64, gemma4:26b-a4b"})
    void of_memory_recommendsTheTier(final long gb, final String tag) {
        assertThat(ModelRecommendation.of(gb * GB).orElseThrow().recommended()).isEqualTo(tag);
    }

    @Test
    void of_35gb_offersNoLargerModel() {
        assertThat(ModelRecommendation.of(35 * GB).orElseThrow().optIn()).isEmpty();
    }

    @Test
    void of_36gb_offersThe31b() {
        assertThat(ModelRecommendation.of(36 * GB).orElseThrow().optIn()).hasValue("gemma4:31b");
    }

    @Test
    void of_reportedJustBelowAWholeGigabyte_roundsToTheNearest() {
        final ModelRecommendation rec =
                ModelRecommendation.of(16 * GB - 300_000_000L).orElseThrow();
        assertThat(rec.gb()).isEqualTo(16);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void of_unknownMemory_isEmpty(final long bytes) {
        assertThat(ModelRecommendation.of(bytes)).isEmpty();
    }

    @Test
    void pullCommand_tag_isTextOnly() {
        assertThat(ModelRecommendation.pullCommand("gemma4:e4b")).isEqualTo("ollama pull gemma4:e4b");
    }

    // The advice is Ollama's: shown for Ollama's connection or an Ollama server, never for another engine.
    @ParameterizedTest
    @CsvSource({
        "OLLAMA, OLLAMA, true",
        "OPENAI_COMPATIBLE, OLLAMA, true",
        "OPENAI_COMPATIBLE, LM_STUDIO, false",
        "OPENAI_COMPATIBLE, GENERIC, false"
    })
    void isOllamaServer_kindAndEngine_decidesTheAdvice(
            final ProviderKind kind, final ServerEngine engine, final boolean expected) {
        assertThat(ModelRecommendation.isOllamaServer(kind, engine)).isEqualTo(expected);
    }
}
