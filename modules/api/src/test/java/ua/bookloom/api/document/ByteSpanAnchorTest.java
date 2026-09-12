package ua.bookloom.api.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code ByteSpanAnchor}'s construction-time invariants — mechanical shape tests.
 */
class ByteSpanAnchorTest {

    @ParameterizedTest
    @CsvSource({"-1, 5", "-3, -1", "5, 4", "0, -1"})
    void constructor_malformedRange_isRejected(int start, int end) {
        assertThatThrownBy(() -> new ByteSpanAnchor(start, end)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_emptySpan_isAccepted() {
        assertThat(new ByteSpanAnchor(7, 7).length()).isZero();
    }

    @Test
    void length_reportsTheSpanWidth() {
        assertThat(new ByteSpanAnchor(8, 13).length()).isEqualTo(5);
    }
}
