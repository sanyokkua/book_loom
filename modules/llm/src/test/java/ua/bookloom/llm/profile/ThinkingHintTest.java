package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ServerEngine;

class ThinkingHintTest {

    private static final ReasoningControl UNSWITCHABLE =
            new ReasoningControl(ReasoningControl.Kind.UNSWITCHABLE, List.of(), null, "on");
    private final ModelIdentityParser parser = new ModelIdentityParser();

    private java.util.Optional<String> tag(final ServerEngine engine, final String model, final ReasoningControl kind) {
        return ThinkingHint.libraryTag(engine, model, parser.parse(model).identity(), kind);
    }

    @ParameterizedTest
    @CsvSource({
        "hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M, qwen3.5:4b",
        "hf.co/unsloth/Qwen3.5-9B-GGUF:Q4_K_M, qwen3.5:9b",
        "hf.co/bartowski/Qwen_Qwen3-8B-GGUF:Q4_K_M, qwen3:8b"
    })
    void libraryTag_hfDownloadOfQwenOnOllama_namesTheLibraryModel(final String model, final String expected) {
        assertThat(tag(ServerEngine.OLLAMA, model, UNSWITCHABLE)).hasValue(expected);
    }

    @Test
    void libraryTag_libraryModel_hasNoHint() {
        assertThat(tag(ServerEngine.OLLAMA, "qwen3.5:9b", UNSWITCHABLE)).isEmpty();
    }

    @Test
    void libraryTag_alwaysThinkingByDesign_hasNoHint() {
        final ReasoningControl always = new ReasoningControl(ReasoningControl.Kind.ALWAYS, List.of(), null, "on");

        assertThat(tag(ServerEngine.OLLAMA, "hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M", always))
                .isEmpty();
        assertThat(tag(ServerEngine.OLLAMA, "gpt-oss:20b", always)).isEmpty();
    }

    @Test
    void libraryTag_switchableOrOtherEngine_hasNoHint() {
        assertThat(tag(ServerEngine.OLLAMA, "hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M", ReasoningControl.NONE))
                .isEmpty();
        assertThat(tag(ServerEngine.LM_STUDIO, "hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M", UNSWITCHABLE))
                .isEmpty();
    }
}
