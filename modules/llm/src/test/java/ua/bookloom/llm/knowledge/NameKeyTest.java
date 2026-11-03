package ua.bookloom.llm.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NameKeyTest {

    @ParameterizedTest
    @CsvSource({
        "qwen2.5, 'qwen,2.5'",
        "gemma3n, 'gemma,3n'",
        "llama-3.1, 'llama,3.1'",
        "mistral-small-3.2, 'mistral,small,3.2'",
        "nemotron-3.5-bolt, 'nemotron,3.5,bolt'",
        "deepseek-r1, 'deepseek,r,1'",
        "phi-4-mini, 'phi,4,mini'",
        "phi4-mini, 'phi,4,mini'",
        "gpt-oss:20b, 'gpt,oss,20b'"
    })
    void tokens_name_splitsOnSeparatorsAndLetterToDigit(final String name, final String expected) {
        assertThat(NameKey.tokens(name)).isEqualTo(List.of(expected.split(",")));
    }
}
