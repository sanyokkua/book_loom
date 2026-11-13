package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
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

    @ParameterizedTest(name = "[{index}] «{0}» -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {"Он зажёг фонарь.|true", "У фонаря стояли.|true", "Шёл с фонарём.|true", "Фонарщик ушёл.|false"})
    void occursIn_group_matchesAnyForm(final String text, final boolean expected) {
        assertThat(TermMatch.occursIn(List.of("фонарь", "фонаря", "фонарём"), text))
                .isEqualTo(expected);
    }

    @Test
    void matches_group_returnsEveryFormInTextOrder() {
        assertThat(TermMatch.matches(List.of("фонарь", "фонаря"), "У фонаря был фонарь."))
                .containsExactly("фонаря", "фонарь");
    }

    @ParameterizedTest(name = "[{index}] {0} in «{1}» -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Mr|Mrs Pellbury came|false",
                "Mr|Mr Quill came|true",
                "Mr|Ms Quill came|false",
                "Mrs|Mrs Pellbury came|true",
                "lantern|Two lanterns hung.|true",
                "Orc|The Orcs came.|true"
            })
    void shortTerm_pluralEnding_isNotTolerated(final String term, final String text, final boolean expected) {
        assertThat(TermMatch.occursIn(term, text)).isEqualTo(expected);
        assertThat(TermMatch.isNamedIn(term, text)).isEqualTo(expected);
    }
}
