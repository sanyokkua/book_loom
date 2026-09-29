package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The whole-word matcher: a letter or digit touching the term on either side is not a match, in any script. */
class WholeWordTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Hale|Hale opened the door.|true",
                "Hale|Hale's coat|true",
                "Hale|A whale and a Whale-boat.|false",
                "Hale|Whaleback|false",
                "Гейл|Гейл пішов.|true",
                "Гейл|Гейлі пішли.|false",
                "hale|Hale opened the door.|false"
            })
    void pattern_term_matchesOnlyAWholeCaseSensitiveWord(final String term, final String text, final boolean expected) {
        assertThat(WholeWord.pattern(term).matcher(text).find()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Hale", "a.b", "(x)"})
    void pattern_sameTerm_answersTheCachedPattern(final String term) {
        assertThat(WholeWord.pattern(term)).isSameAs(WholeWord.pattern(term));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"a.b|a.b is here|true", "a.b|axb is here|false"})
    void pattern_termWithRegexCharacters_isMatchedLiterally(
            final String term, final String text, final boolean expected) {
        assertThat(WholeWord.pattern(term).matcher(text).find()).isEqualTo(expected);
    }
}
