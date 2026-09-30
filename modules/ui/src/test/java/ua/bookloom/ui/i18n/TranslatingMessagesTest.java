package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The Translating screen's counted control reads the plural form of the interface language. */
class TranslatingMessagesTest {

    private static Messages messagesFor(final String tag) {
        return new Messages(new FixedLocaleProvider(Locale.forLanguageTag(tag)));
    }

    // IF Ukrainian used one form for every count, THEN 1 and 3 would read with the wrong agreement.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "uk|1|Переглянути позначений (1)",
                "uk|3|Переглянути позначені (3)",
                "uk|5|Переглянути позначені (5)",
                "en|1|Review flagged (1)",
                "en|3|Review flagged (3)"
            })
    void reviewFlagged_eachLanguage_selectsTheFormOfTheCount(final String tag, final int count, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.TRANSLATING_REVIEW_FLAGGED, count))
                .isEqualTo(expected);
    }
}
