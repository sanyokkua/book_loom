package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The words of the Translating strip, the help line and the live card exist in both languages and agree in number. */
class TranslatingStripMessagesTest {

    private static Messages messagesFor(final String tag) {
        return new Messages(new FixedLocaleProvider(Locale.forLanguageTag(tag)));
    }

    // IF a new key had no entry in a language, THEN that window would show the raw key.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "TRANSLATING_STRIP_FINISHED_IN",
                "TRANSLATING_STRIP_DONE",
                "TRANSLATING_STRIP_TRANSLATED",
                "TRANSLATING_STRIP_FIXED",
                "TRANSLATING_STRIP_TO_REVIEW",
                "TRANSLATING_STRIP_KEPT",
                "TRANSLATING_STRIP_SECOND_LOOK",
                "TRANSLATING_STRIP_ELAPSED",
                "TRANSLATING_STRIP_REVIEW_TIP",
                "TRANSLATING_HELP_SLEEP",
                "TRANSLATING_HELP_SLEEP_OFF",
                "TRANSLATING_HELP_CLOSE_TIP",
                "LIVE_TAB_LIVE",
                "LIVE_MODE_TIP",
                "LIVE_FOLLOW",
                "LIVE_FOLLOW_TIP",
                "LIVE_FOLLOW_AGAIN",
                "LIVE_FOLLOW_AGAIN_TIP",
                "LIVE_ALL_CALLS",
                "LIVE_ALL_CALLS_TIP",
                "LIVE_LOG_ALL",
                "LIVE_LOG_PROBLEMS",
                "LIVE_LOG_PROBLEMS_TIP",
                "LIVE_DETAIL_PENDING",
                "LIVE_DETAIL_NOT_YET",
                "LIVE_DETAIL_NOT_SENT",
                "LIVE_WAITING_FOR_MODEL",
                "LIVE_SUMMARY_NONE",
                "LIVE_SUMMARY_SOURCE"
            })
    void key_inEachLanguage_hasAnEntryThatIsNotTheKeyItself(final String name) {
        final MessageKey key = MessageKey.valueOf(name);

        assertThat(messagesFor("en").get(key, "1", "2", "3")).isNotBlank().isNotEqualTo(key.key());
        assertThat(messagesFor("uk").get(key, "1", "2", "3")).isNotBlank().isNotEqualTo(key.key());
    }

    // IF the number were formatted with the wrong grouping, THEN "1,240" would read as one point two four.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en|791|1240|791 of 1,240 segments done",
                "en|0|1|0 of 1 segment done",
                "uk|791|1240|Готово 791 з 1 240 сегментів",
                "uk|1|21|Готово 1 з 21 сегмента",
                "uk|2|3|Готово 2 з 3 сегментів"
            })
    void done_eachLanguage_groupsTheNumberAndPicksTheFormOfTheTotal(
            final String tag, final int done, final int total, final String expected) {
        final String text = messagesFor(tag).get(MessageKey.TRANSLATING_STRIP_DONE, done, total);

        assertThat(text.replaceAll("[\\s\\u00A0\\u202F]", " ")).isEqualTo(expected);
    }

    // IF the waiting words kept English in Ukrainian, THEN the live line would be half unreadable.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"en|Waiting for the model… 0:12", "uk|Очікування відповіді моделі… 0:12"})
    void waitingForModel_eachLanguage_isTheWordsThenTheClock(final String tag, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.LIVE_WAITING_FOR_MODEL, "0:12"))
                .isEqualTo(expected);
    }
}
