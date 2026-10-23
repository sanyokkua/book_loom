package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A stored choice decides the interface language; otherwise a Ukrainian operating system gets Ukrainian, any other English. */
class OsLocaleProviderTest {

    // The stored choice wins over the operating system; with none stored the OS language decides.
    @ParameterizedTest
    @CsvSource(
            value = {
                "uk-UA, NONE, uk",
                "uk, NONE, uk",
                "de-DE, NONE, en",
                "en-US, NONE, en",
                "fr, NONE, en",
                "de-DE, uk, uk",
                "uk-UA, en, en",
                "uk-UA, xx, uk",
                "de-DE, xx, en",
            },
            nullValues = "NONE")
    void displayLocale_osAndStored_storedWinsElseOs(
            final String osTag, final String storedTag, final String expectedLanguage) {
        final LocaleProvider provider =
                new OsLocaleProvider(() -> Optional.ofNullable(storedTag), () -> Locale.forLanguageTag(osTag));

        assertThat(provider.displayLocale().getLanguage()).isEqualTo(expectedLanguage);
    }

    @ParameterizedTest
    @CsvSource({"uk-UA, uk", "de-DE, en"})
    void forOs_osLocale_ukrainianOnlyForUkrainian(final String osTag, final String expectedLanguage) {
        assertThat(OsLocaleProvider.forOs(Locale.forLanguageTag(osTag)).getLanguage())
                .isEqualTo(expectedLanguage);
    }
}
