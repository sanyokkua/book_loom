package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.i18n.Messages;

/** When a project was last opened, worded against a fixed clock: today, yesterday, or the date. */
class LastOpenedTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);

    private static LastOpened in(final Locale locale) {
        return new LastOpened(NOW, new Messages(() -> locale));
    }

    // IF the day boundary were taken in hours instead of calendar days, THEN 23:00 yesterday would read "today".
    @ParameterizedTest
    @CsvSource({
        "2026-10-22T00:00:00Z, today",
        "2026-10-22T11:59:00Z, today",
        "2026-10-21T23:00:00Z, yesterday",
        "2026-10-21T00:00:00Z, yesterday",
        "2026-10-16T10:00:00Z, Oct 16",
        "2025-01-02T10:00:00Z, Jan 2",
    })
    void text_english_saysTodayYesterdayOrTheDate(final Instant opened, final String expected) {
        assertThat(in(Locale.ENGLISH).text(opened)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-22T08:00:00Z, сьогодні",
        "2026-10-21T08:00:00Z, учора",
    })
    void text_ukrainian_saysTodayOrYesterdayInUkrainian(final Instant opened, final String expected) {
        assertThat(in(Locale.forLanguageTag("uk")).text(opened)).isEqualTo(expected);
    }

    @Test
    void text_aTimeInTheFuture_isStillToday() {
        // IF a clock that moved back made the row read a negative age, THEN the row would show a nonsense date.
        assertThat(in(Locale.ENGLISH).text(Instant.parse("2026-10-23T09:00:00Z")))
                .isEqualTo("today");
    }
}
