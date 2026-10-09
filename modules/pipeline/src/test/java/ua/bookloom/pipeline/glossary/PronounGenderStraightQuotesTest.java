package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.glossary.PronounGender.Evidence;

/** A pronoun a speaker says inside straight quotes is the speech's, never the name's (15h.E1; the Chrome shape). */
class PronounGenderStraightQuotesTest {

    private static final List<String> SPEECH_ABOUT_ANOTHER_MAN = List.of(
            "Chrome nodded. \"Ask Finn, he knows the way,\" said Tiger.",
            "Chrome waited by the door. \"He never pays,\" said Tiger.",
            "Chrome looked up. \"Take him to the dock,\" said Tiger.",
            "Chrome turned away. \"He is late again,\" said Tiger.");

    // IF the straight-quoted "he" were counted, THEN a woman's name would be handed a man's gender.
    @Test
    void of_pronounInsideStraightQuotedSpeech_isNotCountedForTheName() {
        final Evidence evidence = PronounGender.of("Chrome", SPEECH_ABOUT_ANOTHER_MAN, "en");

        assertThat(evidence.male()).isZero();
        assertThat(evidence.dominant()).isEmpty();
    }
}
