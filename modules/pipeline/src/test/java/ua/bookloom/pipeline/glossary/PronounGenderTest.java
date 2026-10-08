package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.PronounGender.Evidence;

class PronounGenderTest {

    private static final List<String> FEMALE_TEXTS = List.of(
            "Chisel poured the tea. She smiled at the cup.",
            "Chisel stopped at the door. She looked back at her friends.",
            "Chisel was tired; she sat.",
            "Chisel sat down.");

    private static GlossaryEntry entry(final String term, final TermType type) {
        return new GlossaryEntry("id-" + term, "p1", term, null, type, Gender.UNKNOWN, false);
    }

    @Test
    void of_pronounsAfterTheName_countsEachOccurrenceOnce() {
        final Evidence evidence = PronounGender.of("Chisel", FEMALE_TEXTS, "en");

        assertThat(evidence.female()).isEqualTo(3);
        assertThat(evidence.male()).isZero();
    }

    @Test
    void of_pronounInsideQuotedSpeech_isNotCounted() {
        final Evidence evidence =
                PronounGender.of("Flint", List.of("Flint said, “I will help him.” He nodded at the door."), "en");

        assertThat(evidence.male()).isEqualTo(1);
        assertThat(evidence.female()).isZero();
    }

    @Test
    void of_anotherNameBetweenNameAndPronoun_isNotCounted() {
        final Evidence evidence = PronounGender.of("Flint", List.of("Flint saw Chisel and she ran."), "en");

        assertThat(evidence.total()).isZero();
    }

    @Test
    void of_languageWithoutPronounTable_isEmpty() {
        assertThat(PronounGender.of("Chisel", FEMALE_TEXTS, "xx").total()).isZero();
    }

    @Test
    void of_declinedNonLatinName_isFound() {
        final Evidence evidence = PronounGender.of(
                "Чизел", List.of("Чизел налила чаю. Она устала.", "Чизела никто не видел. Она ушла."), "ru");

        assertThat(evidence.female()).isEqualTo(2);
    }

    @Test
    void seeded_dominantFemaleEvidence_suggestsTheGender() {
        final GlossaryEntry seeded = PronounGender.seeded(entry("Chisel", TermType.CHARACTER), FEMALE_TEXTS, "en");

        assertThat(seeded.gender()).isEqualTo(Gender.FEMALE);
        assertThat(seeded.isGenderSuggested()).isTrue();
    }

    @Test
    void seeded_aTermWithPersonPronouns_becomesACharacterSuggestion() {
        final GlossaryEntry seeded = PronounGender.seeded(entry("Chisel", TermType.TERM), FEMALE_TEXTS, "en");

        assertThat(seeded.type()).isEqualTo(TermType.CHARACTER);
        assertThat(seeded.gender()).isEqualTo(Gender.FEMALE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLACE", "TITLE"})
    void seeded_placeOrTitle_staysAsItIs(final String type) {
        final GlossaryEntry placed = entry("Chisel", TermType.valueOf(type));

        assertThat(PronounGender.seeded(placed, FEMALE_TEXTS, "en")).isEqualTo(placed);
    }

    @Test
    void seeded_mixedEvidence_suggestsNothing() {
        final List<String> mixed = List.of(
                "Corvin sat. He slept.",
                "Corvin rose. He left.",
                "Corvin ate. She watched.",
                "Corvin ran. She followed.");

        final GlossaryEntry corvin = entry("Corvin", TermType.CHARACTER);

        assertThat(PronounGender.seeded(corvin, mixed, "en")).isEqualTo(corvin);
    }

    @Test
    void seeded_twoOccurrencesOnly_isTooLittleEvidence() {
        final GlossaryEntry chisel = entry("Chisel", TermType.CHARACTER);

        assertThat(PronounGender.seeded(chisel, FEMALE_TEXTS.subList(0, 2), "en"))
                .isEqualTo(chisel);
    }

    @Test
    void seeded_genderThePersonSet_isNeverTouched() {
        final GlossaryEntry set = entry("Chisel", TermType.CHARACTER).withGender(Gender.UNKNOWN);

        assertThat(PronounGender.seeded(set, FEMALE_TEXTS, "en")).isEqualTo(set);
    }

    @Test
    void seeded_knownGender_isNeverOverwritten() {
        final GlossaryEntry known = entry("Chisel", TermType.CHARACTER).withGender(Gender.MALE);

        assertThat(PronounGender.seeded(known, FEMALE_TEXTS, "en")).isEqualTo(known);
    }

    @Test
    void compact_evidence_namesThePronounsSeen() {
        assertThat(PronounGender.of("Chisel", FEMALE_TEXTS, "en").compact()).isEqualTo("she ×3");
        assertThat(new Evidence(List.of(), List.of()).compact()).isEmpty();
    }
}
