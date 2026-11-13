package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArchitectureAliasesTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "qwen3_5,qwen35",
        "qwen35,qwen35",
        "qwen3_5_moe,qwen35moe",
        "gemma4_unified,gemma4",
        "gemma4,gemma4",
        "muse_glimmer,muse-glimmer",
        "muse-glimmer,muse-glimmer",
        "glm4_moe_lite,glm4moelite",
        "glm4moelite,glm4moelite",
        "' Qwen3_5 ',qwen35",
        "deepseek2,deepseek2"
    })
    void normalise_alias_returnsTheCanonicalName(final String alias, final String canonical) {
        assertThat(ArchitectureAliases.normalise(alias)).isEqualTo(canonical);
    }
}
