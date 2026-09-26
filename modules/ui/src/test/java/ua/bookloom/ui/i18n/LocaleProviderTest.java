package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A Ukrainian operating system gets a Ukrainian interface and every other one gets English; nothing asks. */
class LocaleProviderTest {

    // IF the OS language is Ukrainian, THEN the display language is uk; for any other OS language it is en.
    @ParameterizedTest
    @CsvSource({
        "uk-UA, uk",
        "uk, uk",
        "de-DE, en",
        "en-US, en",
        "fr, en",
    })
    void displayLocale_osLanguage_isUkrainianOnlyForUkrainian(final String osTag, final String expectedLanguage) {
        final LocaleProvider provider = new OsLocaleProvider(() -> Locale.forLanguageTag(osTag));

        assertThat(provider.displayLocale().getLanguage()).isEqualTo(expectedLanguage);
    }
}
