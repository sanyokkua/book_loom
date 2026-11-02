package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Quant tokens are found between name separators in every family, and a long name is read promptly. */
class QuantTokensTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {
                "model-q4_k_m.gguf~q4_k_m",
                "model-ud-q4_k_xl~ud-q4_k_xl",
                "m:iq3_xxs~iq3_xxs",
                "m_tq1_0_x~tq1_0",
                "x.bf16.y~bf16",
                "a-mxfp4_moe~mxfp4_moe",
                "a@nvfp4~nvfp4",
                "model-4bit~4bit",
                "m-w4a16-ct~w4a16-ct",
                "m-int8~int8",
                "q8_0~q8_0"
            })
    void find_oneTokenInAName_givesItsSpan(final String name, final String token) {
        final List<QuantTokens.Span> spans = QuantTokens.find(name);

        assertThat(spans).hasSize(1);
        assertThat(name.substring(spans.getFirst().start(), spans.getFirst().end()))
                .isEqualTo(token);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {"qwen3~false", "model-q4x~false", "9bit~false", "bf16x~false", "q4_k_m~true", "UD-Q4~false"})
    void isToken_wholeText_tellsIfItIsOneToken(final String text, final boolean expected) {
        assertThat(QuantTokens.isToken(text)).isEqualTo(expected);
    }

    @Test
    void replaceAll_twoTokens_replacesBoth() {
        assertThat(QuantTokens.replaceAll("m-q4_k_m-bf16", "-")).isEqualTo("m----");
    }

    @Test
    void find_longNameOfManyTokens_isPrompt() {
        final String name = "q4_".repeat(100_000) + "!";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThat(QuantTokens.find(name)).hasSize(100_000));
    }
}
