package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The one display-text rule: no placeholder tokens, single spaces, trimmed. */
class DisplayTextTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"He opened the ⟦g0⟧old⟦g1⟧ door.|He opened the old door.", "⟦g0⟧⟦g1⟧|", "  a ⟦g2⟧  b |a b"})
    void of_maskedText_removesTokensAndCollapsesWhitespace(final String masked, final String expected) {
        assertThat(DisplayText.of(masked)).isEqualTo(expected == null ? "" : expected);
    }
}
