package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/** The size of a written book as the export-complete dialog words it. */
class FileSizesTest {

    // IF the unit were chosen wrongly, THEN a 936 KB book would read as 0.9 MB or as 958464 B.
    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {"512;512 B", "1023;1023 B", "958464;936 KB", "1572864;1.5 MB", "0;0 B", "3145728;3 MB"})
    void format_english_picksTheUnitAndRounds(final long bytes, final String expected) {
        final Messages messages = new Messages((LocaleProvider) () -> Locale.ENGLISH);

        assertThat(FileSizes.format(bytes, messages)).isEqualTo(expected);
    }

    // IF the decimal separator ignored the display language, THEN a Ukrainian dialog would read 1.5 instead of 1,5.
    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {"1572864;1,5 МБ", "958464;936 КБ", "512;512 Б"})
    void format_ukrainian_usesTheLocalUnitAndSeparator(final long bytes, final String expected) {
        final Messages messages = new Messages((LocaleProvider) () -> Locale.forLanguageTag("uk"));

        assertThat(FileSizes.format(bytes, messages)).isEqualTo(expected);
    }
}
