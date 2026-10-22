package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/** The one owner of how a word count is worded: exact below a thousand, rounded to the hundred from there up. */
class WordCountTest {

    private static Messages messages(final Locale locale) {
        return new Messages((LocaleProvider) () -> locale);
    }

    // IF the figure were rounded below a thousand, THEN a short text would claim a count it does not have; IF it were
    // not rounded above, THEN a long book would show a precision the count does not have.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "1|1 word",
                "640|640 words",
                "999|999 words",
                "1000|about 1,000 words",
                "1049|about 1,000 words",
                "1050|about 1,100 words",
                "4100|about 4,100 words",
                "78214|about 78,200 words"
            })
    void text_english_isExactBelowAThousandAndRoundedToTheHundredAbove(final int words, final String expected) {
        assertThat(WordCount.text(messages(Locale.ENGLISH), words)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"1|1 слово", "3|3 слова", "5|5 слів", "21|21 слово", "640|640 слів", "78214|близько 78 200 слів"})
    void text_ukrainian_usesThePluralFormOfTheExactCountAndAboutAbove(final int words, final String expected) {
        assertThat(WordCount.text(messages(Locale.forLanguageTag("uk")), words)).isEqualTo(expected);
    }

    // The grouping space is the locale's own, so only the words around the figure are pinned.
    @Test
    void text_ukrainianLongBook_saysAboutThenTheRoundedFigure() {
        assertThat(WordCount.text(messages(Locale.forLanguageTag("uk")), 78_214))
                .startsWith("близько 78")
                .endsWith("200 слів");
    }
}
