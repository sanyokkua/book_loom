package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

class NameMissingCheckTest {

    private static GlossaryEntry entry(
            final String term, final String target, final TermType type, final boolean locked) {
        return new GlossaryEntry("g", "p", term, target, type, Gender.UNKNOWN, locked);
    }

    @ParameterizedTest
    @CsvSource({"Нелл повільно відчинила двері.", "Нелла повільно відчинила двері.", "Двері повільно відчинила Нелл."})
    void lost_nameKeptOrDeclined_saysNothing(final String target) {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.lost(glossary, "Nell opened the door slowly.", target, "uk"))
                .isEmpty();
    }

    @Test
    void lost_nameDroppedFromTheTarget_namesTheTermAndItsTarget() {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        final List<GlossaryEntry> lost =
                NameMissingCheck.lost(glossary, "Nell opened the door slowly.", "Вона відчинила двері.", "uk");

        assertThat(lost).extracting(GlossaryEntry::term, GlossaryEntry::target).containsExactly(tuple("Nell", "Нелл"));
    }

    @Test
    void lost_possessiveInTheSource_stillCountsAsTheName() {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.lost(glossary, "Nell's door was open.", "Двері були відчинені.", "uk"))
                .isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource({"TERM,false,Нелл", "CHARACTER,true,Нелл", "CHARACTER,false,''"})
    void lost_entryThatIsNotAnUnlockedNameWithATarget_isNotChecked(
            final TermType type, final boolean locked, final String target) {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", target, type, locked));

        assertThat(NameMissingCheck.lost(glossary, "Nell opened the door.", "Вона відчинила двері.", "uk"))
                .isEmpty();
    }

    @Test
    void lost_nameNotInTheSource_saysNothing() {
        final List<GlossaryEntry> glossary = List.of(entry("Nell", "Нелл", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.lost(glossary, "The door opened slowly.", "Двері повільно відчинилися.", "uk"))
                .isEmpty();
    }

    // Names, hyphenated and derived forms from the owner's run: declined, adjectival, alternating, spelled twice.
    @ParameterizedTest(name = "[{index}] {0} -> {1} in «{3}»")
    @CsvSource(
            delimiter = '|',
            value = {
                "Bartimaeus|Бартімей|B-Bartimaeus came back.|Б-Бартімей повернувся.",
                "Bartimaeus|Бартімей|Bartimaeus-senior came back.|Бартімей-старший повернувся.",
                "London|Лондон|The London police came.|Лондонського поліцейського викликали.",
                "Prague|Прага|The Prague papers came.|Празькі газети надійшли.",
                "Prague|Прага|Prague came.|Прага прийшла."
            })
    void lost_declinedHyphenatedOrDerivedName_isNotLost(
            final String term, final String target, final String source, final String translated) {
        final List<GlossaryEntry> glossary = List.of(entry(term, target, TermType.PLACE, false));

        assertThat(NameMissingCheck.lost(glossary, source, translated, "uk")).isEmpty();
    }

    @Test
    void lost_twoSpellingsOfOneName_isNotLostWhenOneIsThere() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Maurice", "Моріс", TermType.CHARACTER, false),
                entry("Maurice", "Морис", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.lost(glossary, "Maurice came.", "Моріс прийшов.", "uk"))
                .isEmpty();
    }

    @Test
    void lost_genuinelyMissingNameBesideAPresentOne_isStillReportedWithTermAndTarget() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Maurice", "Моріс", TermType.CHARACTER, false),
                entry("Bartimaeus", "Бартімей", TermType.CHARACTER, false));

        final List<GlossaryEntry> lost =
                NameMissingCheck.lost(glossary, "Maurice and Bartimaeus came.", "Моріс і хтось прийшли.", "uk");

        assertThat(lost).extracting(GlossaryEntry::term).containsExactly("Bartimaeus");
    }

    @Test
    void lost_languageWithNoAlternationData_keepsThePlainPrefixRule() {
        final List<GlossaryEntry> glossary = List.of(entry("Prague", "Прага", TermType.PLACE, false));

        assertThat(NameMissingCheck.lost(glossary, "Prague came.", "Празькі газети надійшли.", "xx"))
                .isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource({"Марта,Марка", "Тарас,Тамас", "Олена,Олега"})
    void lost_missingNameHiddenOnlyByADifferentConsonant_isStillReported(final String lost, final String other) {
        final List<GlossaryEntry> glossary = List.of(
                entry("Martha", lost, TermType.CHARACTER, false), entry("Mark", other, TermType.CHARACTER, false));

        final List<GlossaryEntry> missing =
                NameMissingCheck.lost(glossary, "Martha and Mark came.", other + " прийшов.", "uk");

        assertThat(missing)
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactly(tuple("Martha", lost));
    }

    @Test
    void lost_doubledLetterSpelling_isOneName() {
        final List<GlossaryEntry> glossary = List.of(
                entry("Nell", "Нелл", TermType.CHARACTER, false), entry("Nel", "Нел", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.lost(glossary, "Nell and Nel came.", "Нелл прийшла.", "uk"))
                .isEmpty();
    }

    @Test
    void lost_hyphenatedRenderingDeclinedInBothParts_isNotLost() {
        final List<GlossaryEntry> glossary =
                List.of(entry("Gentleman Loser", "Джентльмен-Лузер", TermType.PLACE, false));

        assertThat(NameMissingCheck.lost(
                        glossary, "Bobby drank in the Gentleman Loser.", "Боббі пив у Джентльмені-Лузері.", "uk"))
                .isEmpty();
    }

    @Test
    void lost_verbThatSpellsACharactersName_isNotTheCharacter() {
        final List<GlossaryEntry> glossary = List.of(entry("Jack", "Джек", TermType.CHARACTER, false));

        assertThat(NameMissingCheck.lost(
                        glossary, "You can jack into the matrix.", "Можна підключитися до матриці.", "uk"))
                .isEmpty();
    }
}
