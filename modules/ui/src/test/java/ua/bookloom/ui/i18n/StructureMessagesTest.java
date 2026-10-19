package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The structure screen's counted messages pick the plural form of their language. */
class StructureMessagesTest {

    private static Messages messagesFor(final String tag) {
        return new Messages(new FixedLocaleProvider(Locale.forLanguageTag(tag)));
    }

    // IF the English warning kept the singular for every count, THEN "2 segments exceeds" would be shown.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "1|1 segment exceeds the chunk budget — it will be split by sentence and re-joined.",
                "2|2 segments exceed the chunk budget — they will be split by sentence and re-joined.",
            })
    void oversized_english_selectsTheFormOfTheCount(final int count, final String expected) {
        assertThat(messagesFor("en").get(MessageKey.STRUCTURE_CHECK_OVERSIZED, count))
                .isEqualTo(expected);
    }

    // IF Ukrainian used one form for all, THEN 3 and 5 segments would be worded with the noun and verb of 1.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "1|1 сегмент перевищує бюджет чанка — його буде розбито за реченнями й зібрано назад.",
                "3|3 сегменти перевищують бюджет чанка — їх буде розбито за реченнями й зібрано назад.",
                "5|5 сегментів перевищують бюджет чанка — їх буде розбито за реченнями й зібрано назад.",
            })
    void oversized_ukrainian_selectsOneFewAndManyForms(final int count, final String expected) {
        assertThat(messagesFor("uk").get(MessageKey.STRUCTURE_CHECK_OVERSIZED, count))
                .isEqualTo(expected);
    }

    // IF the segment noun in "N of M segments" ignored M, THEN "of 1 segments" would be shown.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en|1,239|1240|Round-trip check failed — structure not preserved (1,239 of 1,240 segments)",
                "en|0|1|Round-trip check failed — structure not preserved (0 of 1 segment)",
                "uk|4|5|Перевірку циклу не пройдено — структуру не збережено (4 з 5 сегментів)",
                "uk|20|21|Перевірку циклу не пройдено — структуру не збережено (20 з 21 сегмента)",
            })
    void failedCounts_bothLanguages_agreesTheNounWithTheSourceCount(
            final String tag, final String copy, final int source, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.STRUCTURE_CHECK_FAILED_COUNTS, copy, source))
                .isEqualTo(expected);
    }
}
