package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ModelReportTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void new_nonPositiveLoaded_throws(final int loaded) {
        assertThatThrownBy(() -> ModelReport.windowsOnly(4096, loaded)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void new_nonPositiveContextLength_throws(final int length) {
        assertThatThrownBy(() -> ModelReport.windowsOnly(length, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
