package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** The edit-distance similarity measure {@link EchoCheck} and the translation memory share. */
class TextSimilarityTest {

    @Test
    void similarity_identicalTexts_isOne() {
        assertThat(TextSimilarity.similarity("He opened the old door.", "He opened the old door."))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void similarity_caseOnlyDifference_isOne() {
        assertThat(TextSimilarity.similarity("He opened the old door.", "HE OPENED THE OLD DOOR."))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void similarity_composedAndDecomposedAccent_matchesAfterNfcNormalization() {
        final String precomposed = "café";
        final String decomposed = "café";

        assertThat(TextSimilarity.similarity(precomposed, decomposed)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void similarity_oneSubstitution_isTwoThirds() {
        assertThat(TextSimilarity.similarity("abc", "abd")).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void similarity_bothEmpty_isOne() {
        assertThat(TextSimilarity.similarity("", "")).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void similarity_wholelyDifferentTexts_isLow() {
        assertThat(TextSimilarity.similarity("He opened the old door.", "Він відчинив старі двері."))
                .isLessThan(0.20);
    }
}
