package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.state.ReadableText.Kind;
import ua.bookloom.ui.state.ReadableText.Piece;

/** How a masked text is cut for the readable view: tokens whole, evidence at its first occurrence. */
class ReadableTextTest {

    @Test
    void cut_textWithTwoTokens_makesEachTokenOnePieceAmongPlainWords() {
        assertThat(ReadableText.cut("Він ⟦g0⟧старі⟦g1⟧ двері", List.of()))
                .containsExactly(
                        new Piece(Kind.PLAIN, "Він "),
                        new Piece(Kind.TOKEN, "g0"),
                        new Piece(Kind.PLAIN, "старі"),
                        new Piece(Kind.TOKEN, "g1"),
                        new Piece(Kind.PLAIN, " двері"));
    }

    @Test
    void cut_quoteInTheText_marksItsFirstOccurrenceOnly() {
        assertThat(ReadableText.cut("кіт і кіт", List.of("кіт")))
                .containsExactly(new Piece(Kind.EVIDENCE, "кіт"), new Piece(Kind.PLAIN, " і кіт"));
    }

    @Test
    void cut_quoteNotInTheText_marksNothing() {
        assertThat(ReadableText.cut("Він пішов", List.of("вона"))).containsExactly(new Piece(Kind.PLAIN, "Він пішов"));
    }

    // IF a token inside evidence were cut in two, THEN the chip would show half a mark.
    @Test
    void cut_quoteAroundAToken_keepsTheTokenWholeInsideTheMark() {
        assertThat(ReadableText.cut("а ⟦g0⟧бе⟦g1⟧ в", List.of("бе")))
                .containsExactly(
                        new Piece(Kind.PLAIN, "а "),
                        new Piece(Kind.TOKEN, "g0"),
                        new Piece(Kind.EVIDENCE, "бе"),
                        new Piece(Kind.TOKEN, "g1"),
                        new Piece(Kind.PLAIN, " в"));
    }

    @Test
    void cut_emptyText_hasNoPieces() {
        assertThat(ReadableText.cut("", List.of("x"))).isEmpty();
    }

    @Test
    void needsView_plainTextAndNoQuote_isFalse() {
        assertThat(ReadableText.needsView("Він пішов", List.of("вона"))).isFalse();
    }

    @Test
    void needsView_tokenOrQuoteFound_isTrue() {
        assertThat(ReadableText.needsView("а ⟦g0⟧б⟦g1⟧", List.of())).isTrue();
        assertThat(ReadableText.needsView("Він пішов", List.of("пішов"))).isTrue();
    }
}
