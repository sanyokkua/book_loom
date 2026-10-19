package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The four shapes of control code the Oct 9 e4b run answered for « » (invented text, 15h.E1): a code the mapper has to
 * read next to the marks the reply already holds, or leave out when the source has no quotes.
 */
class ControlCodeShapesTest {

    // IF a code stands beside the real « it duplicates, THEN it is dropped and the reply keeps its one mark.
    @Test
    void map_strayCodeBesideARealOpeningMark_dropsTheCode() {
        final Optional<String> mapped =
                ControlCharacterMapper.map("He said, “Hello.”", "Він сказав: \u001c«Привіт.»", "uk");

        assertThat(mapped).contains("Він сказав: «Привіт.»");
    }

    // IF a code sits between two letters, THEN it is the language's apostrophe and not a refusal.
    @Test
    void map_codeBetweenTwoLetters_becomesAnApostrophe() {
        final Optional<String> mapped = ControlCharacterMapper.map("It is five.", "Це п\u001fять.", "uk");

        assertThat(mapped).hasValueSatisfying(text -> assertThat(text).matches("Це п['’ʼ]ять\\."));
    }

    // IF the opening « is real and only its closer is a code, THEN the code closes the quote.
    @Test
    void map_closingCodeAfterARealOpeningGuillemet_closesTheQuote() {
        final Optional<String> mapped =
                ControlCharacterMapper.map("He said, “Hello.”", "Він сказав: «Привіт.\u001c", "uk");

        assertThat(mapped).contains("Він сказав: «Привіт.»");
    }

    // IF the source has no quotes, THEN codes wrapping the reply are dropped and no mark is invented.
    @Test
    void map_codesWrappingAReplyWhoseSourceHasNoQuotes_addsNoQuoteMarks() {
        final Optional<String> mapped = ControlCharacterMapper.map("Hello there.", "\u001cПривіт.\u001c", "uk");

        assertThat(mapped).contains("Привіт.");
    }
}
