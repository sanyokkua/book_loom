package ua.bookloom.document.mask;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The placeholder token grammar {@code ⟦gN⟧}, exercised directly against {@link Placeholders} rather than through
 * a walk — these scenarios are about how a target string is read, not about how a segment is produced.
 */
class PlaceholdersTest {

    // WHILE tokens are matched, the system SHALL treat only text of the form ⟦gN⟧ as a token
    // and SHALL key the placeholder map by the bare index form.
    @Test
    void tokensOf_twoDigitIndexAtEnd_isReadAsOneTokenNotOneDigitPrefix() {
        final String target = "text …⟦g11⟧б⟦g12⟧";

        assertThat(Placeholders.tokensOf(target)).containsExactly("⟦g11⟧", "⟦g12⟧");
    }

    // WHILE tokens are matched, the system SHALL treat only text of the form ⟦gN⟧ as a token
    // and SHALL key the placeholder map by the bare index form.
    // Bracketed text that is not the grammar: the requirement says "one or more ASCII digits", so a bare `g` with
    // none is prose, not a token — and reading it as one would let a book forge a placeholder.
    @ParameterizedTest
    @ValueSource(strings = {"a ⟦g⟧ b", "a ⟦gX⟧ b", "a ⟦⟧ b", "a ⟦g1 b", "a g1⟧ b", "a ⟦ g1 ⟧ b"})
    void tokensOf_bracketedTextThatIsNotTheGrammar_yieldsNoToken(String target) {
        assertThat(Placeholders.tokensOf(target)).isEmpty();
    }

    // WHILE tokens are matched, the system SHALL treat only text of the form ⟦gN⟧ as a token
    // and SHALL key the placeholder map by the bare index form.
    // A leading-zero index is well-formed by the grammar and must be read whole. It is distinct from ⟦g7⟧: the
    // masker never mints it, so a target carrying one has invented a token and the gate must be able to see it.
    @Test
    void tokensOf_leadingZeroIndex_isReadAsItsOwnToken() {
        assertThat(Placeholders.tokensOf("a ⟦g007⟧ b")).containsExactly("⟦g007⟧");
        assertThat(Placeholders.keyOf("⟦g007⟧")).isEqualTo("g007");
    }
}
