package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.i18n.Messages;

class DurationTextTest {

    // IF the hour, minute and second forms were mixed up, THEN a person would read a wrong length of time.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en|3720|1h 02m",
                "en|720|12m",
                "en|45|45s",
                "en|3600|1h 00m",
                "en|59|59s",
                "en|60|1m",
                "uk|3720|1 год 02 хв",
                "uk|720|12 хв",
                "uk|45|45 с"
            })
    void format_seconds_readsInTheDisplayLanguage(final String tag, final long seconds, final String expected) {
        final Messages messages = new Messages(() -> Locale.forLanguageTag(tag));

        assertThat(DurationText.format(messages, Duration.ofSeconds(seconds))).isEqualTo(expected);
    }

    // IF the rounded time left were worded like a plain duration, THEN it would not say it is a guess.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en|1200|about 20 min",
                "en|60|about 1 min",
                "en|7200|about 2 h",
                "en|6000|about 1 h 40 min",
                "uk|1200|близько 20 хв",
                "uk|7200|близько 2 год",
                "uk|6000|близько 1 год 40 хв"
            })
    void about_roundedTimeLeft_readsAsAnApproximationInTheDisplayLanguage(
            final String tag, final long seconds, final String expected) {
        final Messages messages = new Messages(() -> Locale.forLanguageTag(tag));

        assertThat(DurationText.about(messages, Duration.ofSeconds(seconds))).isEqualTo(expected);
    }

    // IF the waiting clock were reformatted, THEN the banner that already shows it would change.
    @ParameterizedTest
    @CsvSource({"0,0:00", "7,0:07", "75,1:15", "3725,62:05"})
    void clock_seconds_readsAsMinutesAndTwoDigitSeconds(final int seconds, final String expected) {
        assertThat(DurationText.clock(seconds)).isEqualTo(expected);
    }
}
