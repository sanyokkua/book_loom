package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** What a name candidate or a model's proposal may not be, on invented names. */
class NameHygieneTest {

    @ParameterizedTest
    @ValueSource(strings = {"Chisel'll", "Automatic Flint'll", "Corvin's", "Flint’d", "Chisel'm"})
    void rejection_englishContractionOrPossessive_isRejected(final String term) {
        assertThat(NameHygiene.rejection(term, List.of(term), "en"))
                .hasValueSatisfying(reason -> assertThat(reason).contains("contraction"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"O'Brien", "D'Arcy", "Mar'yan"})
    void rejection_apostropheInsideAName_isKept(final String term) {
        assertThat(NameHygiene.rejection(term, List.of(term), "en")).isEmpty();
    }

    @Test
    void rejection_apostropheSuffixInAnotherLanguage_isKept() {
        assertThat(NameHygiene.rejection("Дем'яс", List.of("Дем'яс"), "uk")).isEmpty();
    }

    @Test
    void rejection_halfOfAJoinedName_isRejected() {
        final List<String> all = List.of("Flint", "Chisel", "Flint & Chisel");

        assertThat(NameHygiene.rejection("Flint", all, "en"))
                .hasValueSatisfying(reason -> assertThat(reason).contains("fragment"));
        assertThat(NameHygiene.rejection("Chisel", all, "en"))
                .hasValueSatisfying(reason -> assertThat(reason).contains("fragment"));
        assertThat(NameHygiene.rejection("Flint & Chisel", all, "en")).isEmpty();
    }

    @Test
    void rejection_firstNameOfAFullName_isKeptAsAnAlias() {
        assertThat(NameHygiene.rejection("Flint", List.of("Flint", "Flint Corvin"), "en"))
                .isEmpty();
    }

    @Test
    void rejection_headWordOfATitleWithAConnector_isRejected() {
        assertThat(NameHygiene.rejection("House", List.of("House", "House of Leaves"), "en"))
                .isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"The", "And", "Because"})
    void rejection_stopWordAlone_isRejected(final String term) {
        assertThat(NameHygiene.rejection(term, List.of(term), "en"))
                .hasValueSatisfying(reason -> assertThat(reason).contains("common word"));
    }

    @Test
    void rejection_ordinaryName_isKept() {
        assertThat(NameHygiene.rejection("Corvin", List.of("Corvin", "Flint"), "en"))
                .isEmpty();
    }

    @Test
    void rejection_pluralOfAnotherCandidate_isRejected() {
        assertThat(NameHygiene.rejection("Ikons", List.of("Ikon", "Ikons"), "en"))
                .hasValueSatisfying(reason -> assertThat(reason).contains("Ikon"));
        assertThat(NameHygiene.rejection("Ikon", List.of("Ikon", "Ikons"), "en"))
                .isEmpty();
    }

    @Test
    void rejection_pluralSuffixOfAnotherLanguage_followsItsFile() {
        assertThat(NameHygiene.rejection("Ікони", List.of("Ікон", "Ікони"), "uk"))
                .isPresent();
        assertThat(NameHygiene.rejection("Ikons", List.of("Ikon", "Ikons"), "xx"))
                .isEmpty();
    }

    @Test
    void rejection_fragmentWithTheBookText_isJudgedByItsStandaloneUse() {
        final List<String> all = List.of("Flint", "Flint & Chisel");
        final java.util.function.ToIntFunction<String> mostlyAlone = term -> term.equals("Flint") ? 20 : 3;
        final java.util.function.ToIntFunction<String> onlyInside = term -> 3;

        assertThat(NameHygiene.rejection("Flint", all, "en", mostlyAlone)).isEmpty();
        assertThat(NameHygiene.rejection("Flint", all, "en", onlyInside)).isPresent();
    }

    // 15i.N8: a place written alone three times beside 35 uses of "Lantern of Arlen" is a name the book uses on its
    // own, however small its share; once alone it stays a fragment.
    @ParameterizedTest(name = "alone {0} times -> rejected {1}")
    @CsvSource({"3, false", "2, false", "1, true"})
    void rejection_fragmentWrittenAloneTwiceOrMore_isKept(final int alone, final boolean rejected) {
        final List<String> all = List.of("Arlen", "Lantern of Arlen");
        final java.util.function.ToIntFunction<String> counts = term -> term.equals("Arlen") ? 35 + alone : 35;

        assertThat(NameHygiene.rejection("Arlen", all, "en", counts).isPresent())
                .isEqualTo(rejected);
    }
}
