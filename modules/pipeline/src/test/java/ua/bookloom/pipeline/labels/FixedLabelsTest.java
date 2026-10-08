package ua.bookloom.pipeline.labels;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The fixed-label table resolves the whole text of a cover or contents label and nothing else. */
class FixedLabelsTest {

    @ParameterizedTest
    @CsvSource({
        "Cover, Обкладинка",
        "COVER, ОБКЛАДИНКА",
        "  table   of Contents:, Зміст",
        "Title page, Титульна сторінка",
        "About the Author, Про автора",
        "Notes., Примітки"
    })
    void resolve_englishToUkrainian_answersTheWholeTextCaseAndSpacingInsensitive(
            final String text, final String expected) {
        assertThat(FixedLabels.forPair("en", "uk").resolve(text)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"Обложка, Обкладинка", "Содержание, Зміст", "Об авторе, Про автора"})
    void resolve_russianToUkrainian_answersTheWholeText(final String text, final String expected) {
        assertThat(FixedLabels.forPair("ru-RU", "uk").resolve(text)).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"Cover of the Book", "Chapter One", "Covered"})
    void resolve_textThatIsMoreThanALabel_isNotAnswered(final String text) {
        assertThat(FixedLabels.forPair("en", "uk").resolve(text)).isEmpty();
    }

    @Test
    void resolve_pairWithoutATable_answersNothingSoTheModelTranslates() {
        assertThat(FixedLabels.forPair("en", "de").resolve("Cover")).isEmpty();
        assertThat(FixedLabels.forPair(null, "uk").resolve("Cover")).isEmpty();
    }
}
