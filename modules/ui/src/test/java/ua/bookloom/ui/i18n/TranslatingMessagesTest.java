package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The run's counted words read the plural form of the interface language. */
class TranslatingMessagesTest {

    private static Messages messagesFor(final String tag) {
        return new Messages(new FixedLocaleProvider(Locale.forLanguageTag(tag)));
    }

    // IF Ukrainian used one form for every count, THEN 1, 3 and 5 would read with the wrong agreement.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "uk|1|Пропустити цей сегмент",
                "uk|3|Пропустити ці 3 сегменти",
                "uk|5|Пропустити ці 5 сегментів",
                "en|1|Skip this segment",
                "en|4|Skip these 4 segments"
            })
    void skip_eachLanguage_selectsTheFormOfTheCount(final String tag, final int count, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.RUN_ACTION_SKIP, count)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "uk|74|142|4|Готово й збережено 74 з 142 сегментів. 4 сегменти, що перекладаються зараз, буде перекладено"
                        + " наступного разу. Для короткої перерви скористайтеся паузою.",
                "en|74|142|4|74 of 142 segments are done and saved. The 4 being translated now are translated next time."
                        + " For a short break, use Pause.",
                "en|74|142|0|74 of 142 segments are done and saved. For a short break, use Pause."
            })
    void stopQuestion_eachLanguage_namesTheCounts(
            final String tag, final int done, final int total, final int inFlight, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.CONFIRM_STOP_TEXT, done, total, inFlight))
                .isEqualTo(expected);
    }
}
