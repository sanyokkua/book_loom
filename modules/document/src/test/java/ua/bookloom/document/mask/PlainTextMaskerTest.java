package ua.bookloom.document.mask;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A TXT segment has no inline markup to protect, so masking it is a no-op unless the text itself carries a literal
 * {@code ⟦}/{@code ⟧}.
 */
class PlainTextMaskerTest {

    private static final String PLAIN_PARAGRAPH = "Це звичайний абзац без розмітки.";

    // WHILE a segment is masked, the system SHALL leave every piece of character data that is
    // not itself a protected span present and translatable.
    @Test
    void mask_plainTextParagraph_leavesItsProseFullyPresent() {
        final MaskedContent masked = PlainTextMasker.mask(PLAIN_PARAGRAPH);

        assertThat(masked.masked()).isEqualTo(PLAIN_PARAGRAPH);
    }

    // WHILE a plain-text paragraph is masked, the system SHALL leave its masked form equal to
    // its source text with an empty placeholder map.
    @Test
    void mask_plainTextParagraph_masksToItselfWithAnEmptyMap() {
        final MaskedContent masked = PlainTextMasker.mask(PLAIN_PARAGRAPH);

        assertThat(masked.masked()).isEqualTo(PLAIN_PARAGRAPH);
        assertThat(masked.placeholders()).isEmpty();
    }
}
