package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.NameWindows.Window;

/**
 * With the source language Auto and no recognised declaration the code has no analysis language (a null tag): it uses
 * the generic rules — every bundled stop-word list together, no pronoun tables, no plural suffixes — and never reads the
 * book as English.
 */
class NoSourceLanguageReadersTest {

    private static final List<String> FEMALE_TEXTS = List.of(
            "Chisel poured the tea. She smiled at the cup.",
            "Chisel stopped at the door. She looked back at her friends.",
            "Chisel was tired; she sat.",
            "Chisel sat down.");

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }

    private static GlossaryEntry entry(final String term, final TermType type) {
        return new GlossaryEntry("id-" + term, "p1", term, null, type, Gender.UNKNOWN, false);
    }

    private static List<NameCandidate> namesOf(final List<String> lines, final @Nullable String language) {
        final BookSlice book = GlossaryTestSegments.of(lines);
        return FrequencyScan.candidates(book.index(), book.ordinals(), 3, language);
    }

    // --- stop words and titles: the union ---------------------------------------------------------------------

    // IF an unknown language read the English list only, THEN a French function word would pass for a name.
    @ParameterizedTest
    @ValueSource(strings = {"the", "le", "dans", "aber", "nie", "como", "або"})
    void of_noLanguage_isTheUnionOfEveryBundledList(final String word) {
        assertThat(StopWords.of(null)).contains(word);
    }

    // IF a known language borrowed the union, THEN its own words would be judged by other languages' lists.
    @Test
    void of_aLanguageWithItsOwnList_staysThatListAlone() {
        assertThat(StopWords.of("fr")).contains("dans").doesNotContain("aber", "the");
    }

    @Test
    void neverAlone_noLanguage_holdsTheSpelledOutNumbers() {
        assertThat(StopWords.neverAlone(null)).contains("one", "two");
    }

    @Test
    void isTitle_noLanguage_readsTheBundledTitleLists() {
        assertThat(KeyTermScan.isTitle("Captain", null)).isTrue();
        assertThat(KeyTermScan.isTitle("Gravel", null)).isFalse();
    }

    // --- the name scan ----------------------------------------------------------------------------------------

    // IF "Le" were read as an English word, THEN a French title would be proposed as the name "Le Rouge".
    @Test
    void candidates_frenchTitleWithNoLanguage_neverJoinsAFunctionWordToAName() {
        final List<NameCandidate> found = namesOf(copies("Il lisait Le Rouge hier soir.", 4), null);

        assertThat(found).extracting(NameCandidate::term).containsExactly("Rouge");
    }

    // The same book with its language named gives the same answer, so Auto loses nothing by not knowing.
    @Test
    void candidates_sameFrenchBookNamedFrench_givesTheSameAnswer() {
        final List<NameCandidate> found = namesOf(copies("Il lisait Le Rouge hier soir.", 4), "fr");

        assertThat(found).extracting(NameCandidate::term).containsExactly("Rouge");
    }

    // IF English plural suffixes were folded under Auto, THEN a name ending in s would vanish into its neighbour.
    @Test
    void candidates_noLanguage_foldsNoPluralIntoItsBase() {
        final List<String> book = new ArrayList<>(copies("We saw Ember at the door.", 6));
        book.addAll(copies("We saw the Embers at the door.", 3));

        assertThat(namesOf(book, null)).extracting(NameCandidate::term).containsExactlyInAnyOrder("Ember", "Embers");
        assertThat(namesOf(book, "en")).extracting(NameCandidate::term).containsExactly("Ember");
    }

    // --- the recurring-term scan ------------------------------------------------------------------------------

    // IF a French function word were offered to the model as a candidate term, THEN every batch would carry it.
    @Test
    void frequentWords_frenchBookWithNoLanguage_leavesOutAFrenchFunctionWord() {
        final BookSlice book = GlossaryTestSegments.of(copies("Il marchait dans la rue.", 8));

        final List<NameCandidate> found = KeyTermScan.frequentWords(book.index(), book.ordinals(), null, 10);

        assertThat(found).extracting(NameCandidate::term).contains("marchait").doesNotContain("dans");
    }

    // --- pronouns: no table means no code gender evidence -----------------------------------------------------

    // IF English pronouns were read in a book of unknown language, THEN a French "she" would be a guess dressed as
    // proof.
    @Test
    void of_noLanguage_readsNoPronounEvenWhereEnglishWouldFindThree() {
        assertThat(PronounGender.of("Chisel", FEMALE_TEXTS, null).total()).isZero();
        assertThat(PronounGender.of("Chisel", FEMALE_TEXTS, "en").female()).isEqualTo(3);
    }

    @Test
    void seeded_noLanguage_suggestsNoGender() {
        final GlossaryEntry chisel = entry("Chisel", TermType.CHARACTER);

        assertThat(PronounGender.seeded(chisel, FEMALE_TEXTS, null)).isEqualTo(chisel);
    }

    // IF the windows still carried an English pronoun, THEN the reviewer would be shown proof the code does not have.
    @Test
    void of_nameWindowsWithNoLanguage_carryNoPronounAndNoEvidence() {
        final BookSlice book = GlossaryTestSegments.of(copies("Wren stood. She left. The end.", 6));

        final Evidence evidence = NameWindows.of(
                        book.index(), book.ordinals(), List.of(entry("Wren", TermType.CHARACTER)), null)
                .getOrDefault("Wren", Evidence.NONE);

        assertThat(evidence.count()).isEqualTo(6);
        assertThat(evidence.windows()).isNotEmpty().extracting(Window::pronoun).containsOnlyNulls();
        assertThat(evidence.pronouns().total()).isZero();
    }

    // --- first-name seeding -----------------------------------------------------------------------------------

    // IF a first-name list were chosen for an unknown language, THEN "Rosalind" would be a woman in any book.
    @Test
    void seeded_noLanguage_suggestsNoGenderFromAFirstName() {
        final GlossaryEntry rosalind = entry("Rosalind", TermType.CHARACTER);

        assertThat(GivenNames.seeded(rosalind, null)).isEqualTo(rosalind);
        assertThat(GivenNames.seeded(List.of(rosalind), null)).containsExactly(rosalind);
    }

    // --- name hygiene -----------------------------------------------------------------------------------------

    // IF English plural suffixes judged an unknown language, THEN a name ending in s would be dropped as a plural.
    @Test
    void rejection_pluralShapedNameWithNoLanguage_isKept() {
        assertThat(NameHygiene.rejection("Ikons", List.of("Ikon", "Ikons"), null))
                .isEmpty();
        assertThat(NameHygiene.rejection("Ikons", List.of("Ikon", "Ikons"), "en"))
                .isPresent();
    }

    // IF the English contraction rule judged an unknown language, THEN a name ending in 's would be dropped as English.
    @Test
    void rejection_apostropheSNameWithNoLanguage_isKept() {
        assertThat(NameHygiene.rejection("Corvin's", List.of("Corvin's"), null)).isEmpty();
        assertThat(NameHygiene.rejection("Corvin's", List.of("Corvin's"), "en")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Le", "The", "Aber", "Nie"})
    void rejection_functionWordOfAnyBundledLanguageWithNoLanguage_isACommonWord(final String term) {
        assertThat(NameHygiene.rejection(term, List.of(term), null))
                .hasValueSatisfying(reason -> assertThat(reason).contains("common word"));
    }

    @Test
    void rejection_ordinaryNameWithNoLanguage_isKept() {
        assertThat(NameHygiene.rejection("Corvin", Stream.of("Corvin", "Flint").toList(), null))
                .isEmpty();
    }
}
