package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContextLengthTest {

    @Test
    void detected_reportedFigure_isPresent() {
        assertThat(new ContextLength(4096).detected()).hasValue(4096);
    }

    @Test
    void detected_unknown_isEmpty() {
        assertThat(ContextLength.unknown().detected()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_nonPositiveFigure_isRejected(final int tokens) {
        assertThatThrownBy(() -> new ContextLength(tokens)).isInstanceOf(IllegalArgumentException.class);
    }
}
