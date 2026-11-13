package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExpectedTokensTest {

    @Test
    void sequence_textWithTokens_listsThemInSourceOrder() {
        assertThat(ExpectedTokens.sequence("He opened the ⟦g1⟧old⟦g0⟧ door.")).isEqualTo("⟦g1⟧ ⟦g0⟧");
    }

    @Test
    void sequence_textWithoutToken_saysThereIsNone() {
        assertThat(ExpectedTokens.sequence("He opened the old door."))
                .isEqualTo("(none — write no ⟦gN⟧ token at all; write every name as plain text)");
    }
}
