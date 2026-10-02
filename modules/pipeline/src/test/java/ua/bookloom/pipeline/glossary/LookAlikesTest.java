package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A Latin look-alike is repaired only inside a word that is already Cyrillic. */
class LookAlikesTest {

    @ParameterizedTest
    @CsvSource({
        "Елеонора Вeнс,Елеонора Венс",
        "Eleanor Vance,Eleanor Vance",
        "Інститут BBC,Інститут BBC",
        "'Гарроу-Вeйл.','Гарроу-Вейл.'",
        "'',''"
    })
    void repaired_text_replacesLookAlikesOnlyInMixedWords(final String text, final String expected) {
        assertThat(LookAlikes.repaired(text)).isEqualTo(expected);
    }
}
