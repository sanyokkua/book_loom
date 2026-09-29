package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

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
}
