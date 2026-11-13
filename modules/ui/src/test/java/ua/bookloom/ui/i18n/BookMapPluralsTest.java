package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The counted words of Names &amp; style and its names pass take the right plural form in both languages. */
class BookMapPluralsTest {

    private static Messages messages(final String language) {
        return new Messages((LocaleProvider) () -> Locale.forLanguageTag(language));
    }

    // IF the figures line put one form after every number, THEN "з приблизно 21 кроків" would read as a mistake.
    @ParameterizedTest
    @CsvSource({
        "21, 50 % · 10 з приблизно 21 кроку",
        "22, 50 % · 10 з приблизно 22 кроків",
        "49, 50 % · 10 з приблизно 49 кроків",
        "11, 50 % · 10 з приблизно 11 кроків"
    })
    void figures_ukrainian_stepsTakeTheirPluralForm(final int total, final String expected) {
        assertThat(messages("uk").get(MessageKey.PROGRESS_FIGURES, 50, 10, total))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1, 0 % · 0 of about 1 step", "49, 0 % · 0 of about 49 steps"})
    void figures_english_stepsTakeOneOrOther(final int total, final String expected) {
        assertThat(messages("en").get(MessageKey.PROGRESS_FIGURES, 0, 0, total)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1, об’єднує 1 форму", "2, об’єднує 2 форми", "5, об’єднує 5 форм", "21, об’єднує 21 форму"})
    void formsJoined_ukrainian_formsTakeTheirPluralForm(final int forms, final String expected) {
        assertThat(messages("uk").get(MessageKey.NAMES_STYLE_FORMS_JOINED, forms, "Велемира"))
                .isEqualTo(expected + ": Велемира");
    }

    @ParameterizedTest
    @CsvSource({"0, без запитів", "1, 1 запит", "3, 3 запити", "6, 6 запитів", "21, 21 запит"})
    void stageRequests_ukrainian_requestsTakeTheirPluralForm(final int requests, final String expected) {
        assertThat(messages("uk").get(MessageKey.PROGRESS_STAGE_REQUESTS_DONE, requests))
                .isEqualTo(expected);
    }
}
