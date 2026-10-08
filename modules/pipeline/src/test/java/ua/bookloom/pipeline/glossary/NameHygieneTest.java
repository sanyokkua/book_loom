package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
}
