package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * A glossary entry's target is found in a lower-case, declined or vowel-alternating form before a swap or a lost name
 * is raised (15j.J8); the names are invented.
 */
class NameInflectionTest {

    private static final List<String> PAIRS = List.of(
            "Flint → Флінт",
            "Night Council → Нічна рада",
            "Resistance → Опір",
            "Prime Minister → Прем'єр-міністр",
            "Stone → Камінь");
    private static final List<String> UK = List.of("гзж", "кцч", "хсш", "і>о", "і>е");

    @ParameterizedTest
    @CsvSource({
        "The Night Council met at noon., Нічної ради зібралися опівдні. Флінт мовчав.",
        "The Night Council met at noon., нічної ради зібралися опівдні. Флінт мовчав.",
        "Everyone feared the Night Council., Усі боялися Нічної Ради. Флінт мовчав.",
        "The Resistance held the bridge., Опором став міст. Флінт мовчав.",
        "The Resistance held the bridge., Міст тримав опір. Флінт мовчав.",
        "The Stone was cold., Каменя торкнувся Флінт.",
        "He thanked the Prime Minister., Він подякував прем'єр-міністру. Флінт кивнув.",
        "He thanked the Prime Minister., Він подякував прем’єр-міністру. Флінт кивнув."
    })
    void find_declinedLowerCaseOrAlternatingTarget_isNoSwap(final String source, final String target) {
        assertThat(NameSwapCheck.find(source, target, PAIRS, UK, "en")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "The Resistance held the bridge., Флінт тримав міст.",
        "The Night Council met at noon., Флінт зустрівся опівдні.",
        "He thanked the Prime Minister., Він подякував Флінту."
    })
    void find_otherNameStandsForTheSourceName_stillFails(final String source, final String target) {
        assertThat(NameSwapCheck.find(source, target, PAIRS, UK, "en")).hasSize(1);
    }

    // The і→о alternation must not turn one glossary rendering's stem into another's (Тім, Том are two names).
    @ParameterizedTest
    @CsvSource({"Tim opened the door., Тома відчинив двері.", "Tom opened the door., Тіма відчинив двері."})
    void find_alternationEqualsAnotherGlossaryStem_stillASwap(final String source, final String target) {
        final List<String> pairs = List.of("Tim → Тім", "Tom → Том");

        assertThat(NameSwapCheck.find(source, target, pairs, UK, "en")).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
        "The Resistance held the bridge., Міст тримав Опором",
        "The Resistance held the bridge., Під опором став міст",
        "The Night Council met at noon., Збір нічної ради тривав довго",
        "The Night Council met at noon., Збір нічної Ради тривав довго"
    })
    void lost_declinedLowerCaseOrAlternatingTarget_isNotLost(final String source, final String target) {
        final List<GlossaryEntry> glossary = List.of(
                new GlossaryEntry("a", "p", "Resistance", "Опір", TermType.CHARACTER, Gender.UNKNOWN, false),
                new GlossaryEntry("b", "p", "Night Council", "Нічна рада", TermType.CHARACTER, Gender.UNKNOWN, false));

        assertThat(NameMissingCheck.lost(glossary, source, target, "uk")).isEmpty();
    }

    @Test
    void lost_alternatingFormOfADifferentWord_isStillLost() {
        final List<GlossaryEntry> glossary =
                List.of(new GlossaryEntry("a", "p", "Resistance", "Опір", TermType.CHARACTER, Gender.UNKNOWN, false));

        assertThat(NameMissingCheck.lost(glossary, "The Resistance held.", "Міст тримали вони.", "uk"))
                .hasSize(1);
    }

    private static final List<GlossaryEntry> TWO_TIMS = List.of(
            new GlossaryEntry("a", "p", "Tim", "Тім", TermType.CHARACTER, Gender.UNKNOWN, false),
            new GlossaryEntry("b", "p", "Tom", "Том", TermType.CHARACTER, Gender.UNKNOWN, false));

    @Test
    void lost_alternationEqualsAnotherGlossaryStem_isStillLost() {
        assertThat(NameMissingCheck.lost(TWO_TIMS, "Tim and Tom left.", "Том і Тома пішли.", "uk"))
                .extracting(GlossaryEntry::term)
                .containsExactly("Tim");
    }

    @Test
    void lost_bothNamesPresentTogether_isNotLost() {
        assertThat(NameMissingCheck.lost(TWO_TIMS, "Tim and Tom left.", "Тім і Том пішли.", "uk"))
                .isEmpty();
    }

    @Test
    void find_bothNamesPresentTogether_isNoSwap() {
        assertThat(NameSwapCheck.find(
                        "Tim and Tom left.", "Тіма і Тому дали.", List.of("Tim → Тім", "Tom → Том"), UK, "en"))
                .isEmpty();
    }

    @Test
    void find_vocativeAlternationEqualsAnotherGlossaryStem_isStillMissing() {
        assertThat(VocativeCheck.find("Tim, come here.", "Том, іди сюди.", List.of("Tim → Тім", "Tom → Том"), UK, "en"))
                .hasSize(1);
    }
}
