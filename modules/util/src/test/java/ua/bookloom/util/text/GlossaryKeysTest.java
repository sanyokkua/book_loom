package ua.bookloom.util.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** One key per glossary term, whatever case, composition, possessive or edge punctuation it is written with. */
class GlossaryKeysTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Lovelace|lovelace",
                "LOVELACE|lovelace",
                "  Lovelace  |lovelace",
                "Lovelace's|lovelace",
                "Lovelace’s|lovelace",
                "\"Lovelace,\"|lovelace",
                "«Lovelace»|lovelace",
                "Simon   Lovelace|simon lovelace",
                "Al-Arish|al-arish",
                "O'Brien|o'brien",
                "Straße|strasse",
                "Café|café",
                "Ле́ся|ле́ся",
                "...|...",
            })
    void of_variantSpelling_foldsToOneKey(final String term, final String expected) {
        assertThat(GlossaryKeys.of(term)).isEqualTo(expected);
    }
}
