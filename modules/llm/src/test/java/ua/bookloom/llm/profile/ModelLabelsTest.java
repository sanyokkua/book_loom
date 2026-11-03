package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The display name built from an identity: acronyms upper-case, other words capitalised. */
class ModelLabelsTest {

    private final ModelIdentityParser parser = new ModelIdentityParser();

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL|GLM 4.7 Flash",
                "hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M|Qwen 3.5",
                "gemma4:e4b|Gemma 4",
                "gpt-oss:20b|GPT OSS"
            })
    void of_name_buildsTheLabel(final String name, final String expected) {
        assertThat(ModelLabels.of(parser.parse(name).identity())).isEqualTo(expected);
    }

    @Test
    void of_nameWithNoFamily_fallsBackToEmptyText() {
        assertThat(ModelLabels.of(parser.parse("   ").identity())).isEmpty();
    }
}
