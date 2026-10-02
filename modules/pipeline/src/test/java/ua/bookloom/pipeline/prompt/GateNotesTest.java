package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.PlaceholderPair;

/** The repair note names what is wrong with the rejected target and what each affected pair wraps in the source. */
class GateNotesTest {

    private static final List<PlaceholderPair> PAIR = List.of(new PlaceholderPair("⟦g0⟧", "⟦g1⟧", null));
    private static final String RULE = "The placeholders do not match.";

    @Test
    void describe_closingTokenDropped_namesItAndWhatThePairWraps() {
        final String note =
                GateNotes.describe("⟦g0⟧“A⟦g1⟧bove all,” he said.", "⟦g0⟧«Понад усе», — сказав він.", PAIR, RULE);

        assertThat(note).isEqualTo("""
                        The placeholders do not match.
                        Missing (put each back once): ⟦g1⟧.
                        In <Text>, ⟦g0⟧…⟦g1⟧ wraps "“A" — wrap the translation of exactly that text.""");
    }

    @Test
    void describe_inventedAndRepeatedTokens_namesThemWithCounts() {
        final String note = GateNotes.describe("a ⟦g0⟧b⟦g1⟧ c", "а ⟦g0⟧б⟦g1⟧⟦g1⟧ в ⟦g7⟧", PAIR, RULE);

        assertThat(note).isEqualTo("""
                        The placeholders do not match.
                        Extra (remove): ⟦g1⟧.
                        ⟦g7⟧ is not a placeholder of this text; write names as plain text.""");
    }

    @Test
    void describe_tokenInventedForANameInATextWithNone_saysToWriteTheName() {
        final String note = GateNotes.describe("and Vance never let", "і ⟦g1⟧ ніколи не дозволяв", List.of(), RULE);

        assertThat(note).isEqualTo("""
                        The placeholders do not match.
                        ⟦g1⟧ is not a placeholder of this text; write names as plain text.""");
    }

    @Test
    void describe_lineBreakTokenDropped_saysItEndsALine() {
        final String note =
                GateNotes.describe("• Mass.⟦g0⟧\n• Weight.", "• Маса. • Вага.", List.of(), List.of("⟦g0⟧"), RULE);

        assertThat(note).isEqualTo("""
                        The placeholders do not match.
                        Missing (put each back once): ⟦g0⟧.
                        ⟦g0⟧ ends a line: keep it at the end of the same line as in <Text>.""");
    }

    @Test
    void describe_pairReversed_namesTheOrderAndThePair() {
        final String note = GateNotes.describe("a ⟦g0⟧b⟦g1⟧ c", "а ⟦g1⟧б⟦g0⟧ в", PAIR, RULE);

        assertThat(note).isEqualTo("""
                        The placeholders do not match.
                        Out of order: ⟦g1⟧ comes before ⟦g0⟧.
                        In <Text>, ⟦g0⟧…⟦g1⟧ wraps "b" — wrap the translation of exactly that text.""");
    }

    @Test
    void describe_otherRule_statesItAndDescribesEveryPair() {
        final String note = GateNotes.describe("a ⟦g0⟧b⟦g1⟧ c", "⟦g0⟧а б в⟦g1⟧", PAIR, "All words in one pair.");

        assertThat(note).isEqualTo("""
                        All words in one pair.
                        In <Text>, ⟦g0⟧…⟦g1⟧ wraps "b" — wrap the translation of exactly that text.""");
    }
}
