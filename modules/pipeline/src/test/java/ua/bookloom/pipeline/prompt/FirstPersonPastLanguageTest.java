package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Ukrainian language file's key {@code firstPersonPast} (15i.T5): one pattern that
 * finds the narrator's own «я + past-tense verb» and says in a named group which gender the ending shows.
 */
class FirstPersonPastLanguageTest {

    private static Matcher found(final String tag, final String text) {
        final Matcher matcher =
                LanguageRules.bundled().firstPersonPast(tag).orElseThrow().matcher(text);
        assertThat(matcher.find()).as(text).isTrue();
        return matcher;
    }

    @ParameterizedTest
    @CsvSource({
        "Я пішла додому., female",
        "Потім я сказала правду., female",
        "Я не бачила нікого., female",
        "Я пішов додому., male",
        "Потім я сказав правду., male",
        "Я не бачив нікого., male"
    })
    void firstPersonPast_ukrainianFormOfTheNarrator_namesTheGenderItShows(final String text, final String gender) {
        final Matcher matcher = found("uk", text);

        assertThat(matcher.group(gender)).isNotNull();
        assertThat(matcher.group(gender.equals("male") ? "female" : "male")).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Я йду додому.",
                "Він пішов додому.",
                "Вона сказала правду.",
                "Моя пішла додому.",
                "Ми пішли додому."
            })
    void firstPersonPast_textWithoutTheNarratorsPastForm_hasNoMatch(final String text) {
        assertThat(LanguageRules.bundled()
                        .firstPersonPast("uk")
                        .orElseThrow()
                        .matcher(text)
                        .find())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "es", "xx"})
    void firstPersonPast_languageWithNoPattern_isEmpty(final String tag) {
        assertThat(LanguageRules.bundled().firstPersonPast(tag)).isEmpty();
    }

    @Test
    void firstPersonPast_patternHasBothGenderGroups() {
        final String pattern =
                LanguageRules.bundled().firstPersonPast("uk").orElseThrow().pattern();

        assertThat(pattern).contains("(?<female>").contains("(?<male>");
    }
}
