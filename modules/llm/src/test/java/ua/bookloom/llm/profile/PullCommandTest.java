package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The pull command is only text built from a parsed name; nothing here runs it. */
class PullCommandTest {

    private final ModelIdentityParser parser = new ModelIdentityParser();

    @Test
    void of_hubBlobLink_keepsTheQuantLabelAsWritten() {
        final ParsedName parsed =
                parser.parse("https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/blob/main/Qwen3.5-4B-Q4_K_M.gguf");

        assertThat(PullCommand.of(parsed)).contains("ollama pull hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M");
    }

    @Test
    void of_hubIdWithDynamicQuant_keepsThePrefixAndCase() {
        final ParsedName parsed = parser.parse("hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL");

        assertThat(PullCommand.of(parsed)).contains("ollama pull hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL");
    }

    @Test
    void of_plainLibraryTag_isEmpty() {
        assertThat(PullCommand.of(parser.parse("gemma4:e4b"))).isEmpty();
    }

    @Test
    void of_repositoryWithoutAQuant_isEmpty() {
        assertThat(PullCommand.of(parser.parse("hf.co/unsloth/GLM-4.7-Flash-GGUF")))
                .isEmpty();
    }
}
