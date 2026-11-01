package ua.bookloom.util.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Stress marks a model or a dictionary writes over a vowel, removed without touching the letters that own a mark. */
class StressMarksTest {

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "Зо\u0301тов|Зотов|an acute accent",
                "\u0450ва|ева|a precomposed grave accent",
                "Ко\u0300рин|Корин|a combining grave accent",
                "Йо\u0301сип|Йосип|the breve of й stays",
                "\u0407\u0301жак|\u0407жак|the diaeresis of ї stays",
                "Зо\u00B4тов|Зотов|a spacing acute after a Cyrillic vowel",
                "Mak\u00B4s|Mak\u00B4s|a spacing acute after a consonant stays",
                "Зо\u00ADтов|Зотов|a soft hyphen",
                "Pok\u00E9mon|Pok\u00E9mon|a precomposed Latin accent stays",
                "Rene\u0301e|Ren\u00E9e|a combining Latin accent stays",
                "Гейл|Гейл|plain text is unchanged",
            })
    void strip_marks_returnsExpected(final String input, final String expected, final String why) {
        assertThat(StressMarks.strip(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            delimiter = '|',
            value = {"Зо\u0301тов|1", "Зо\u0301то\u0300в\u00AD|3", "Гейл|0", "\u0407жак|0"})
    void count_marks_returnsHowManyWouldGo(final String input, final int expected) {
        assertThat(StressMarks.count(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"Зо\u0301тов", "\u0450ва", "Йо\u0301сип"})
    void strip_twice_isTheSameAsOnce(final String input) {
        assertThat(StressMarks.strip(StressMarks.strip(input))).isEqualTo(StressMarks.strip(input));
    }
}
