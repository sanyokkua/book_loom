package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.glossary.PronounGender.Evidence;

/** A pronoun a speaker says inside straight quotes is the speech's, never the name's (15h.E1; the Ember shape). */
class PronounGenderStraightQuotesTest {

    private static final List<String> SPEECH_ABOUT_ANOTHER_MAN = List.of(
            "Ember nodded. \"Ask Dane, he knows the way,\" said Badger.",
            "Ember waited by the door. \"He never pays,\" said Badger.",
            "Ember looked up. \"Take him to the dock,\" said Badger.",
            "Ember turned away. \"He is late again,\" said Badger.");

    // IF the straight-quoted "he" were counted, THEN a woman's name would be handed a man's gender.
    @Test
    void of_pronounInsideStraightQuotedSpeech_isNotCountedForTheName() {
        final Evidence evidence = PronounGender.of("Ember", SPEECH_ABOUT_ANOTHER_MAN, "en");

        assertThat(evidence.male()).isZero();
        assertThat(evidence.dominant()).isEmpty();
    }
}
