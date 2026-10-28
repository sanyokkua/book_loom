package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvalThresholdsTest {

    @ParameterizedTest
    @CsvSource({
        "gemma4:31b,large",
        "qwen3.5:35b,large",
        "gemma4:26b-mlx,large",
        "gemma4:12b-mlx,mid",
        "gemma4:e4b-mlx,small",
        "qwen2.5:1.5b,small",
        "gemma4:e2b,e2b"
    })
    void classOf_modelIdSize_isParsedFromTheId(final String model, final String expected) {
        assertThat(EvalThresholds.classOf(model)).isEqualTo(expected);
    }
}
