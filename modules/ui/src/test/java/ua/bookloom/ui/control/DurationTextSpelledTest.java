package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.i18n.Messages;

class DurationTextSpelledTest {

    // IF the two largest units were not both spelled, THEN the Results header would read "2 min" for 1 min 48 s.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en|108|1 min 48 s",
                "en|120|2 min",
                "en|45|45 s",
                "en|0|0 s",
                "en|3725|1 h 2 min",
                "uk|108|1 хв 48 с",
                "uk|45|45 с",
                "uk|3725|1 год 2 хв"
            })
    void spelled_seconds_readsTheTwoLargestUnitsInTheDisplayLanguage(
            final String tag, final long seconds, final String expected) {
        final Messages messages = new Messages(() -> Locale.forLanguageTag(tag));

        assertThat(DurationText.spelled(messages, Duration.ofSeconds(seconds))).isEqualTo(expected);
    }
}
