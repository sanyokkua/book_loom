package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The key-term matcher: whole words, any case, an English plural or possessive tolerated. */
class TermMatchTest {

    @ParameterizedTest(name = "[{index}] {0} in «{1}» -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "master|The Master spoke.|true",
                "master|The masters spoke.|true",
                "master|The master's hat.|true",
                "master|A masterpiece.|false",
                "master|Grandmaster Flash.|false",
                "Mr|MR Hale arrived.|true",
                "dark lord|The dark   lord rose.|true",
                "master|They quarrelled.|false"
            })
    void occursIn_texts_matchWholeWordsOnly(final String term, final String text, final boolean expected) {
        assertThat(TermMatch.occursIn(term, text)).isEqualTo(expected);
    }
}
