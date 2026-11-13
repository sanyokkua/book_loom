package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.checks.FunctionWords;

class StopWordsTest {

    @ParameterizedTest
    @ValueSource(strings = {"она", "но"})
    void of_russian_holdsTheFunctionWordsAndNoStopWordFileExists(final String word) {
        assertThat(StopWords.of("ru")).contains(word);
        assertThat(StopWords.class.getResource("stopwords/ru.txt")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"may", "will"})
    void of_english_keepsMayAndWillOutOfTheStopWords(final String word) {
        assertThat(StopWords.of("en")).doesNotContain(word).contains("the");
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "en", "es", "fr", "pl", "uk"})
    void stopWordFiles_everyLanguage_holdNoWordOfItsFunctionWordFile(final String language) throws IOException {
        try (var stream = StopWords.class.getResourceAsStream("stopwords/" + language + ".txt")) {
            final Set<String> extras = Set.copyOf(new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList());
            assertThat(extras).doesNotContainAnyElementsOf(FunctionWords.of(language));
        }
    }

    @Test
    void titles_russianAndUkrainian_areLoaded() {
        assertThat(StopWords.titles("ru")).contains("господин", "граф", "капитан", "доктор");
        assertThat(StopWords.titles("uk")).contains("пан", "пані", "капітан", "доктор");
    }

    @Test
    void of_ukrainian_keepsItsInterjectionExtras() {
        assertThat(StopWords.of("uk")).containsAll(List.of("ах", "ай"));
    }
}
