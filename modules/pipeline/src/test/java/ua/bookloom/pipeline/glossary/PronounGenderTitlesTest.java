package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.PronounGender.Evidence;

/**
 * A surname's pronoun tally reads only the uses that are that name (15i.N gate): a use inside a longer name ("Corin
 * Vale ... she") is the longer name's, a use after a form of address that shows a gender ("Mrs Vale") is that
 * gender's evidence, and when one surname is written after both a man's and a woman's form of address, it is a family
 * name and those uses count for nothing. A name that opens with such a form ("Widow Brisk") has its gender. The books
 * are invented.
 */
class PronounGenderTitlesTest {

    // The bare surname is the husband ("he" ×3); his wife is "Mrs. Vale" ("she" ×4) and his daughter "Corin Vale".
    private static final List<String> FAMILY = List.of(
            "Vale opened the gate. He was late again.",
            "Vale sat by the fire. He said nothing.",
            "At noon Vale left the house. He took the dog.",
            "Mrs. Vale watched the road. She was worried.",
            "Mrs. Vale lit the lamp. She waited.",
            "Mrs. Vale wrote a letter. She sealed it.",
            "Mrs. Vale slept. She dreamt of the sea.",
            "Mr. Vale came home at last.",
            "At dusk Corin Vale laughed. She ran to the gate.",
            "At noon Corin Vale sang. She was happy.");

    @Test
    void of_sharedSurname_countsOnlyTheBareUses() {
        final Evidence evidence = PronounGender.of("Vale", FAMILY, "en");

        assertThat(evidence.male()).isEqualTo(3);
        assertThat(evidence.female()).isZero();
    }

    @Test
    void of_surnameOnlyAfterOneGendersForm_countsTheFormAsEvidence() {
        final Evidence evidence = PronounGender.of(
                "Lutwin",
                List.of(
                        "Ms. Lutwin smiled at the class.",
                        "The children loved Ms. Lutwin, though nobody said so.",
                        "Ms Lutwin closed the book."),
                "en");

        assertThat(evidence.female()).isEqualTo(3);
        assertThat(evidence.male()).isZero();
    }

    @Test
    void of_useInsideALongerName_isTheLongerNamesNotTheShortOnes() {
        final Evidence evidence = PronounGender.of(
                "Vale", List.of("Corin Vale laughed. She ran.", "Then Corin Vale sang. She was happy."), "en");

        assertThat(evidence.total()).isZero();
    }

    @Test
    void of_capitalisedStopWordBeforeTheName_doesNotMakeALongerName() {
        final Evidence evidence = PronounGender.of(
                "Vale", List.of("Then Vale left. He was tired.", "But Vale stayed. He was not."), "en");

        assertThat(evidence.male()).isEqualTo(2);
    }

    @Test
    void of_nameOpeningWithAGenderedForm_hasThatGender() {
        final Evidence evidence = PronounGender.of(
                "Widow Brisk",
                List.of(
                        "Widow Brisk's dog barked. He wanted his dinner.",
                        "Widow Brisk came out. The boy saw her.",
                        "Everyone knew Widow Brisk."),
                "en");

        assertThat(evidence.female()).isEqualTo(3);
        assertThat(evidence.male()).isZero();
    }

    // A capitalised word that opens a sentence may be any word ("Later"), so it joins no name.
    @Test
    void of_capitalisedWordOpeningTheSentenceBeforeTheName_doesNotMakeALongerName() {
        final Evidence evidence = PronounGender.of(
                "Vale",
                List.of("Rain fell. Later Vale left. He was tired.", "“Go.” Afterwards Vale slept. He dreamt."),
                "en");

        assertThat(evidence.male()).isEqualTo(2);
    }

    // IF an Auto source with no recognised declaration (no language) gave no evidence at all, THEN a woman called "Mrs.
    // Whitmore" through the book stayed without a gender; the form of address is read from every language's data.
    @Test
    void of_languageNotKnown_stillReadsTheFormsOfAddress() {
        final List<String> texts = List.of(
                "Good evening, Mrs. Whitmore, said the boy.",
                "Mrs. Whitmore handed him a loaf of bread.",
                "Mr. Ashdown waited by the gate. Whitmore never saw him.");

        assertThat(PronounGender.of("Whitmore", texts, null).female()).isEqualTo(2);
        assertThat(PronounGender.of("Ashdown", texts, null).male()).isEqualTo(1);
        assertThat(PronounGender.of("Whitmore", texts, null).male()).isZero();
    }

    @Test
    void seeded_languageNotKnown_suggestsTheGenderOfTheFormOfAddressAsLikely() {
        final GlossaryEntry entry =
                GlossaryEntry.of("e1", "p1", "Whitmore", null, TermType.CHARACTER, Gender.UNKNOWN, false);
        final GlossaryEntry seeded = PronounGender.seeded(
                entry, List.of("Mrs. Whitmore handed him a loaf.", "Good evening, Mrs. Whitmore."), null);

        assertThat(seeded.gender()).isEqualTo(Gender.FEMALE);
        assertThat(seeded.genderOrigin()).isEqualTo(Origin.LIKELY);
    }

    @Test
    void of_languageNotKnownAndNoFormOfAddress_isNoEvidence() {
        assertThat(PronounGender.of("Whitmore", List.of("Whitmore handed him a loaf. She smiled."), null)
                        .total())
                .isZero();
    }
}
