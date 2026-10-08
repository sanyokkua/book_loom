package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The book's own spacing wins, and a control character is never mistaken for spacing. */
class WhitespaceRestorationTest {

    @Test
    void restore_sourceWithRealSpacing_keepsItAroundTheCandidate() {
        assertThat(WhitespaceRestoration.restore("  Hi\n", "Привіт")).isEqualTo("  Привіт\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u001c", "\u001d", "\u001e", "\u001f"})
    void restore_sourceEdgedByControl_doesNotTreatItAsSpacing(final String control) {
        assertThat(WhitespaceRestoration.restore(control + "Hi" + control, "Привіт"))
                .isEqualTo("Привіт");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u001c", "\u001d", "\u001e", "\u001f", "\u0013", "\u0000", "\u007f"})
    void addsControl_textWithControlTheSourceLacks_isTrue(final String control) {
        assertThat(ControlCharacters.addsControl("Come on, I said", "Давай" + control + ", сказав"))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Давай, сказав я.", "Рядок\nдругий\tтаб\r\n", "«Цюрих» — Джек…"})
    void addsControl_ordinaryProse_isFalse(final String text) {
        assertThat(ControlCharacters.addsControl("", text)).isFalse();
    }
}
