package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * Under Transliterate a rendering must start the way the source transliterates (15i.N6): word by word, the opening
 * letter or digraph of each source word maps through the Ukrainian letter table. The table is data in uk.properties;
 * the check judges the opening of each word only and never the rest of it.
 */
class TransliterationCheckTest {

    private static final Map<String, List<String>> UK = LanguageRules.bundled().translit("uk");

    // IF a word of the rendering opens with a letter the source word cannot give, THEN the rendering does not hold.
    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "Arlo|Арло|true",
                "Arlo|Карло|false",
                "ARLO|АРЛО|true",
                "Venn|Венн|true",
                "Venn|Вен|true",
                "Venn|Кенн|false",
                "Wren|Врен|true",
                "Quill|Квіл|true",
                "Jasper|Джаспер|true",
                "Yara|Яра|true",
                "Xavier|Ксав'єр|true",
                "Ellis|Еліс|true",
                "O'Neil|О'Ніл|true",
                // a digraph is read as a digraph: Sh is Ш, never С then Х
                "Thorne|Торн|true",
                "Thorne|Сорн|false",
                "Sheldon|Шелдон|true",
                "Sheldon|Селдон|false",
                "Chester|Честер|true",
                "Chester|Тестер|false",
                "Philip|Філіп|true",
                "Philip|Пілліп|false",
                "Khan|Хан|true",
                "Zhenya|Женя|true",
            })
    void holds_oneWord_comparesOnlyTheOpening(final String term, final String rendering, final boolean expected) {
        assertThat(TransliterationCheck.holds(term, rendering, UK)).isEqualTo(expected);
    }

    // Each word is judged against the word in the same place, so a wrong second word fails the whole name.
    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {
                "Arlo Venn|Арло Венн|true",
                "Arlo Venn|Арло Кенн|false",
                "Arlo Venn|Карло Венн|false",
                "Arlo Venn|Арло|false",
                "Arlo|Арло Венн|false",
                "Mary-Anne|Мері-Енн|true",
                "Mary-Anne|Мері Енн|true",
                "Mary-Anne|Мері-Кенн|false",
            })
    void holds_severalWords_judgesEachWordAndTheirNumber(
            final String term, final String rendering, final boolean expected) {
        assertThat(TransliterationCheck.holds(term, rendering, UK)).isEqualTo(expected);
    }

    // A word the table has no row for cannot be judged, so it is not refused.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            quoteCharacter = '"',
            value = {"Ørsted|Орстед", "Арло|Arlo", "1984|Тисяча", "Arlo 7|Арло 7"})
    void holds_wordTheTableCannotJudge_isNotRefused(final String term, final String rendering) {
        assertThat(TransliterationCheck.holds(term, rendering, UK)).isTrue();
    }

    @Test
    void holds_emptyTable_judgesNothing() {
        assertThat(TransliterationCheck.holds("Arlo", "Карло", Map.of())).isTrue();
    }
}
