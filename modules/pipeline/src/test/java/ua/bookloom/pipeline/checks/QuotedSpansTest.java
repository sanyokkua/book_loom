package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** What is left of a paragraph once its speakers' words are blanked out. */
class QuotedSpansTest {

    @Test
    void narration_quotedSpeech_isBlankedWithTheSameLength() {
        final String text = "Я сказав: «Йди геть» і пішов.";

        final String narration = QuotedSpans.narration(text, "uk");

        assertThat(narration).hasSameSizeAs(text).isEqualTo("Я сказав:            і пішов.");
    }

    @Test
    void narration_nestedQuotes_areBlankedToTheOuterClose() {
        assertThat(QuotedSpans.narration("A «b „c“ d» e", "uk")).isEqualTo("A           e");
    }

    @Test
    void narration_unclosedQuote_blanksTheRest() {
        assertThat(QuotedSpans.narration("Я пішов. «Далі", "uk")).isEqualTo("Я пішов.      ");
    }

    @Test
    void narration_dashDialogue_keepsOnlyTheAuthorsRemark() {
        final String text = "— Іди, — сказав він, — і не барись.";

        assertThat(QuotedSpans.narration(text, "uk")).isEqualTo("         сказав він,               ");
    }

    @Test
    void narration_paragraphWithoutLeadingDash_isNotSplitOnItsDashes() {
        final String text = "Я зачинив двері — і пішов.";

        assertThat(QuotedSpans.narration(text, "uk")).isEqualTo(text);
    }
}
