package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EvalWindowTest {

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "null, null, 16384",
                "null, 131072, 16384",
                "null, 8192, 8192",
                "'', 4096, 4096",
                "32768, 4096, 32768",
                "' 2048 ', null, 2048"
            })
    void resolve_askedAndDetected_followsTheJobsRule(final String asked, final Integer detected, final int expected) {
        assertThat(EvalWindow.resolve(asked, detected)).isEqualTo(expected);
    }
}
