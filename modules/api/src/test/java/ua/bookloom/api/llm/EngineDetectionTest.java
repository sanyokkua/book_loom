package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
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
}
