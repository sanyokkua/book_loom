package ua.bookloom.llm.client.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ReasoningLevel;

/** Proves the decided level is worded as Ollama's own {@code think} value, with no rule read from a model's name. */
class OllamaThinkControlTest {

    @ParameterizedTest
    @CsvSource({"OFF,false", "LOW,low", "ON,true", ","})
    void thinkControl_decidedLevel_isOllamasOwnValue(
            final @Nullable ReasoningLevel level, final @Nullable String expected) {
        final Object control = OllamaReasoning.thinkControl(level);

        assertThat(control == null ? null : String.valueOf(control)).isEqualTo(expected);
    }
}
