package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The three things a person may choose map to what is stored and what is shown. */
class LanguageChoiceTest {

    @ParameterizedTest
    @CsvSource({"uk, UKRAINIAN", "en, ENGLISH", "de, SYSTEM"})
    void of_storedTag_givesItsChoice(final String tag, final LanguageChoice expected) {
        assertThat(LanguageChoice.of(Optional.of(tag))).isEqualTo(expected);
    }

    @Test
    void of_nothingStored_isSystem() {
        assertThat(LanguageChoice.of(Optional.empty())).isEqualTo(LanguageChoice.SYSTEM);
    }

    @Test
    void tag_systemChoice_isEmptyOthersTheirTag() {
        assertThat(LanguageChoice.SYSTEM.tag()).isEmpty();
        assertThat(LanguageChoice.UKRAINIAN.tag()).contains("uk");
        assertThat(LanguageChoice.ENGLISH.tag()).contains("en");
    }

    @ParameterizedTest
    @CsvSource({"SYSTEM, uk-UA, uk", "SYSTEM, de-DE, en", "ENGLISH, uk-UA, en", "UKRAINIAN, de-DE, uk"})
    void resolve_osLocale_systemFollowsItOthersIgnoreIt(
            final LanguageChoice choice, final String osTag, final String expectedLanguage) {
        assertThat(choice.resolve(Locale.forLanguageTag(osTag)).getLanguage()).isEqualTo(expectedLanguage);
    }
}
