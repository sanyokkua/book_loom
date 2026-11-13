package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;

/**
 * The character gender sheet the reviewer, the neighbour check and the checks read: only the characters the text
 * names, a gender only when the person set it or the book proved it.
 */
class CharacterSheetTest {

    private static final GlossaryEntry LYRA =
            ContextFixtures.entry("Lyra", "Ліра", TermType.CHARACTER, Gender.FEMALE, false);
    private static final GlossaryEntry HALE =
            ContextFixtures.entry("Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry NOBODY =
            ContextFixtures.entry("Quill", "Квіл", TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final GlossaryEntry PLACE =
            ContextFixtures.entry("Oxford", "Оксфорд", TermType.PLACE, Gender.NEUTER, false);
    private static final GlossaryEntry LYRA_SUGGESTED = ContextFixtures.entry(
                    "Lyra", "Ліра", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.FEMALE);
    private static final GlossaryEntry LYRA_VERIFIED = ContextFixtures.entry(
                    "Lyra", "Ліра", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withInferredGender(Gender.FEMALE);

    private static List<String> select(final String text) {
        return select(text, List.of(LYRA, HALE, NOBODY, PLACE));
    }

    private static List<String> select(final String text, final List<GlossaryEntry> glossary) {
        return CharacterSheet.select(List.of(ContextFixtures.segment(0, text)), glossary, "en");
    }

    @Test
    void select_textNamesOneCharacter_listsOnlyThatCharactersGender() {
        assertThat(select("Lyra walked to Oxford with Quill.")).containsExactly("Lyra — female");
    }

    @Test
    void select_pronounsFollowTheName_showTheEvidenceCompactly() {
        assertThat(select("Lyra walked home. She sat down, and Hale watched her."))
                .containsExactly("Lyra — female (she ×1)", "Hale — male");
    }

    // IF a count against the set gender were shown, THEN the sheet would argue with itself ("Hale — male (her ×1)").
    @Test
    void select_countsAgainstTheSetGender_areLeftOut() {
        assertThat(select("Hale shook her hand. Hale waved her off.")).containsExactly("Hale — male");
    }

    // IF a short name had a line of its own, THEN one person would read as two on the sheet.
    @Test
    void select_shortNameOfAFullName_isFoldedIntoTheFullNamesLine() {
        final GlossaryEntry danny = ContextFixtures.entry("Danny", "Денні", TermType.CHARACTER, Gender.MALE, false);
        final GlossaryEntry dannyVosk =
                ContextFixtures.entry("Danny Vosk", "Денні Воск", TermType.CHARACTER, Gender.MALE, false);

        assertThat(select("Danny Vosk came in. Later Danny left. He was tired.", List.of(danny, dannyVosk)))
                .containsExactly("Danny Vosk (also Danny) — male (he ×1)");
    }

    @Test
    void select_textNamesNoCharacter_hasNoSheet() {
        assertThat(select("The rain fell on Oxford.")).isEmpty();
    }

    @Test
    void select_twoCharacters_listsBothInGlossaryOrder() {
        assertThat(select("Hale met Lyra.")).containsExactly("Lyra — female", "Hale — male");
    }

    // IF a guessed gender were listed, THEN the model and every check after it would treat a guess as the person's
    // word; the character is listed by name alone (design D5).
    @Test
    void select_genderOnlySuggested_isListedByNameAlone() {
        assertThat(select("Lyra walked to Oxford.", List.of(LYRA_SUGGESTED, PLACE)))
                .containsExactly("Lyra");
    }

    // Arm 4b (task 7.8) measured the hint line "Pell — male (likely)" and it brought no fewer agreement errors.
    @Test
    void select_likelyGender_showsNameOnly() {
        final List<GlossaryEntry> glossary =
                List.of(ContextFixtures.entry("Pell", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                        .withGenderFrom(Gender.MALE, Origin.LIKELY));

        assertThat(select("Pell laughed.", glossary)).containsExactly("Pell");
    }

    @Test
    void select_genderVerified_isListedAsAFact() {
        assertThat(select("Lyra walked home.", List.of(LYRA_VERIFIED))).containsExactly("Lyra — female");
    }

    @Test
    void select_genderSetByThePerson_isListedAsAFact() {
        assertThat(select("Lyra walked home.", List.of(LYRA.withGender(Gender.FEMALE))))
                .containsExactly("Lyra — female");
    }
}
