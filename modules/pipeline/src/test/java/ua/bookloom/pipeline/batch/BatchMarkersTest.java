package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.Tokens;

/** The item markers of the marked-passage arm (15i.X1) are no placeholder: they are written and stripped by code. */
class BatchMarkersTest {

    @ParameterizedTest
    @CsvSource({"1,⟦s1⟧", "8,⟦s8⟧", "12,⟦s12⟧", "a.b,⟦sa.b⟧"})
    void of_id_isTheMarkerThatStartsThatItem(final String id, final String expected) {
        assertThat(BatchMarkers.of(id)).isEqualTo(expected);
    }

    // IF a marker survives into a text, THEN it goes before any check reads the text, with the space that follows it.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "⟦s1⟧ Alpha|Alpha",
                "⟦s1⟧Alpha|Alpha",
                "Alpha ⟦s2⟧ Beta|Alpha Beta",
                "Alpha ⟦s2⟧Beta|Alpha Beta",
                "⟦s1⟧ Alpha ⟦s2⟧ Beta ⟦s12⟧ Gamma|Alpha Beta Gamma",
                "⟦g0⟧Alpha⟦g1⟧ ⟦s3⟧ end|⟦g0⟧Alpha⟦g1⟧ end",
                "⟦g0⟧Alpha⟦g1⟧ and more|⟦g0⟧Alpha⟦g1⟧ and more",
                "Alpha ⟦s⟧ Beta|Alpha ⟦s⟧ Beta"
            })
    void strip_textWithMarkers_dropsOnlyTheMarkers(final String text, final String expected) {
        assertThat(BatchMarkers.strip(text)).isEqualTo(expected);
    }

    // The placeholder gate and the token-multiset guards read tokens by the ⟦gN⟧ grammar, which an s-marker is not.
    @Test
    void tokensInOrder_textWithMarkers_listsOnlyThePlaceholders() {
        assertThat(Tokens.inOrder("⟦s1⟧ ⟦g0⟧Alpha⟦g1⟧ ⟦s2⟧ ⟦g2⟧")).containsExactly("⟦g0⟧", "⟦g1⟧", "⟦g2⟧");
    }
}
