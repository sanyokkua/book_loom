package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * The character sheet folds a short name into a full name by the same clusters the glossary uses (15i.N2): a one-word
 * name that is the first or last word of exactly one full character name. The sheet's other behaviour is covered by
 * {@code ContextPackageCharactersTest}.
 */
class InjectedCharactersClustersTest {

    // IF the first full name holding the word took the short name, THEN two brothers' surname would be folded into
    // whichever came first in the glossary, and the sheet would name a brother who is not in the scene.
    @Test
    void select_surnameTwoMenShare_keepsItsOwnLineAndFoldsIntoNeither() {
        final List<GlossaryEntry> glossary = List.of(
                character("Arlo Venn", Gender.MALE),
                character("Bram Venn", Gender.MALE),
                character("Venn", Gender.MALE));

        final List<String> sheet = select("Venn nodded.", glossary);

        assertThat(sheet).containsExactly("Venn — male");
    }

    // The middle word of a three-word name is no short form of it.
    @Test
    void select_wordInTheMiddleOfAFullName_isNotFoldedIntoIt() {
        final List<GlossaryEntry> glossary =
                List.of(character("Arlo Dane Venn", Gender.MALE), character("Dane", Gender.MALE));

        assertThat(select("Dane nodded.", glossary)).containsExactly("Dane — male");
    }

    @Test
    void select_honorificFormOfAFullName_isFoldedIntoItsLine() {
        final List<GlossaryEntry> glossary =
                List.of(character("Arlo Venn", Gender.MALE), character("Mr Venn", Gender.MALE));

        assertThat(select("Arlo Venn came in. Mr Venn sat down.", glossary))
                .containsExactly("Arlo Venn (also Mr Venn) — male");
    }

    // IF Mrs Venn were folded into the man's line, THEN the sheet would tell the model the wife is male.
    @Test
    void select_mrsAndAManOfTheSameSurname_areTwoLines() {
        final List<GlossaryEntry> glossary =
                List.of(character("Arlo Venn", Gender.MALE), character("Mrs Venn", Gender.FEMALE));

        assertThat(select("Arlo Venn came in. Mrs Venn sat down.", glossary))
                .containsExactly("Arlo Venn — male", "Mrs Venn — female");
    }

    // One person, one gender: a short name the glossary has no gender for yet is the full name's person, so the sheet
    // tells the model so rather than leaving the name out.
    // IF the unknown short name were left out, THEN a scene naming only "Danny" would have no gender for the model.
    @Test
    void select_shortNameWithNoGenderOfAKnownFullName_isFoldedAndTakesItsGender() {
        final List<GlossaryEntry> glossary =
                List.of(character("Danny Vosk", Gender.MALE), character("Danny", Gender.UNKNOWN));

        assertThat(select("Danny left.", glossary)).containsExactly("Danny Vosk (also Danny) — male");
    }

    // Two people the person gave different genders are two lines, however alike the names.
    @Test
    void select_shortAndFullNameWithDifferentGenders_keepTwoLines() {
        final List<GlossaryEntry> glossary =
                List.of(character("Danny Vosk", Gender.MALE), character("Danny", Gender.FEMALE));

        assertThat(select("Danny Vosk met Danny.", glossary)).containsExactly("Danny Vosk — male", "Danny — female");
    }

    @Test
    void select_placeSharingAWordWithACharacter_isNotACharacterLine() {
        final List<GlossaryEntry> glossary = List.of(
                character("Venn", Gender.FEMALE),
                new GlossaryEntry("p1:Venn Hall", "p1", "Venn Hall", null, TermType.PLACE, Gender.NEUTER, false));

        assertThat(select("Venn walked to Venn Hall.", glossary)).containsExactly("Venn — female");
    }

    private static List<String> select(final String text, final List<GlossaryEntry> glossary) {
        final Segment segment = ContextFixtures.segment(0, text);
        return InjectedCharacters.select(List.of(segment), glossary);
    }

    private static GlossaryEntry character(final String term, final Gender gender) {
        return ContextFixtures.entry(term, null, TermType.CHARACTER, gender, false);
    }
}
