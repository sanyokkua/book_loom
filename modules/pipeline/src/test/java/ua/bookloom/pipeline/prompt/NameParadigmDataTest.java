package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The name-form rows are data in each language's file; only their shape is fixed here, the readers come later. */
class NameParadigmDataTest {

    private static final Pattern ROW = Pattern.compile("(\\S+)\\|(m|f|any)(>voc)?: (\\S+(?: \\S+)*)");

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk"})
    void rows_everyShippedParadigmRow_parsesToAnEndingAGenderAndAtLeastOneForm(final String tag) throws IOException {
        final Properties properties = LanguageFilesTest.file("languages/" + tag + ".properties");
        final List<String> rows = properties.stringPropertyNames().stream()
                .filter(key -> key.matches("nameParadigms\\.\\d+"))
                .map(properties::getProperty)
                .toList();

        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row).matches(ROW));
    }

    @Test
    void nounEndings_russian_holdsTheListedEndings() throws IOException {
        final String endings = LanguageFilesTest.file("languages/ru.properties").getProperty("nounEndings");

        assertThat(endings.split(" ")).contains("ь", "ей", "ем", "ём", "ю", "я", "а", "ы", "и", "у", "е", "ом", "ой");
    }
}
