package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;

class EngineDetectionTest {

    @Test
    void new_negativeProbeCount_isRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EngineDetection(
                        ServerEngine.GENERIC, null, false, -1, Source.DETECTED, Suitability.SUITABLE, false));
    }

    @Test
    void sourceToken_eachSource_isTheLogWord() {
        assertThat(Source.DETECTED.token()).isEqualTo("detected");
        assertThat(Source.OVERRIDE.token()).isEqualTo("override");
    }

    @Test
    void label_llamaCpp_isTheServersOwnName() {
        assertThat(ServerEngine.LLAMA_CPP.label()).isEqualTo("llama-server");
        assertThat(ServerEngine.LM_STUDIO.label()).isEqualTo("LM Studio");
    }

    // IF the engine could be read from anywhere in the details, THEN a model name such as "engine=OLLAMA" could pose.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "reason=engine-mismatch, endpointHost=h:1, engine=LLAMA_CPP | LLAMA_CPP",
                "engine=UNSLOTH | UNSLOTH",
            })
    void in_detailsNamingAnEngine_readsIt(final String details, final ServerEngine expected) {
        assertThat(ServerEngine.in(details)).contains(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"model=engine=OLLAMA", "engine=NOT_AN_ENGINE", "reason=auth"})
    void in_detailsWithoutAKnownEngine_isEmpty(final String details) {
        assertThat(ServerEngine.in(details)).isEmpty();
    }

    @Test
    void read_noteOfFourChecks_givesEachValueInOrder() {
        assertThat(AdvancedCheck.read("schema=honoured; thinking=reasoning_effort; samplers=ignored; cap=max_tokens"))
                .containsExactly(
                        entry(AdvancedCheck.SCHEMA, "honoured"),
                        entry(AdvancedCheck.THINKING, "reasoning_effort"),
                        entry(AdvancedCheck.SAMPLERS, "ignored"),
                        entry(AdvancedCheck.CAP, "max_tokens"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"structured output: supported", "unknown=1"})
    void read_noteWithNoCheck_isEmpty(final String note) {
        assertThat(AdvancedCheck.read(note)).isEmpty();
    }

    @Test
    void pair_cap_isTheTokenAndTheValue() {
        assertThat(AdvancedCheck.CAP.pair(AdvancedCheck.NO_FIELD)).isEqualTo("cap=none");
    }
}
