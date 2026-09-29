package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The one placeholder-token pattern. */
class TokensTest {

    @Test
    void inOrder_textWithTokens_answersThemInTextOrder() {
        assertThat(Tokens.inOrder("A ⟦g2⟧ b ⟦g0⟧ ⟦g10⟧")).containsExactly("⟦g2⟧", "⟦g0⟧", "⟦g10⟧");
    }

    @Test
    void inOrder_textWithoutTokens_answersEmpty() {
        assertThat(Tokens.inOrder("plain ⟦x⟧ text")).isEqualTo(List.of());
    }

    @Test
    void replace_textWithTokens_replacesEachToken() {
        assertThat(Tokens.replace("He ⟦g0⟧old⟦g1⟧ door.", " ")).isEqualTo("He  old  door.");
    }

    @Test
    void replace_emptyReplacement_removesTheTokens() {
        assertThat(Tokens.replace("⟦g0⟧a⟦g1⟧", "")).isEqualTo("a");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "He ⟦g0⟧old⟦g1⟧ door.|1",
                "⟦g7⟧ and ⟦g2⟧|7",
                "⟦g10⟧ and ⟦g9⟧|10",
                "plain ⟦x⟧ text|-1",
                "He typed g0.|-1",
            })
    void highestIndex_text_answersTheLargestTokenNumberOrMinusOne(final String text, final int expected) {
        assertThat(Tokens.highestIndex(text)).isEqualTo(expected);
    }

    @Test
    void highestIndex_emptyText_answersMinusOne() {
        assertThat(Tokens.highestIndex("")).isEqualTo(-1);
    }
}
