package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class FunctionWordFilesTest {

    private static final List<String> LANGUAGES =
            List.of("en", "uk", "ru", "de", "es", "fr", "pl", "cs", "sk", "hr", "sl", "pt");

    static Stream<Arguments> orderedPairs() {
        return LANGUAGES.stream()
                .flatMap(source -> LANGUAGES.stream()
                        .filter(target -> !target.equals(source))
                        .map(target -> Arguments.of(source, target)));
    }

    private static List<String> lines(final String language) throws IOException {
        try (var stream = FunctionWords.class.getResourceAsStream("function-words/" + language + ".txt")) {
            assertThat(stream).as("file for %s", language).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "uk", "ru", "de", "es", "fr", "pl", "cs", "sk", "hr", "sl", "pt"})
    void of_everyShippedFile_isLowerCaseOneWordPerLineWithoutDuplicates(final String language) throws IOException {
        final List<String> lines = lines(language);
        assertThat(lines).isNotEmpty().allSatisfy(line -> {
            assertThat(line).isNotBlank().doesNotContain(" ").isEqualTo(line.toLowerCase(Locale.ROOT));
        });
        assertThat(Set.copyOf(lines)).hasSameSizeAs(lines);
        assertThat(FunctionWords.of(language)).hasSameSizeAs(lines);
    }

    @ParameterizedTest
    @MethodSource("orderedPairs")
    void sourceOnly_everyPairOfTwoShippedLanguages_holdsNoWordOfTheTargetFile(
            final String source, final String target) {
        assertThat(FunctionWords.sourceOnly(source, target)).doesNotContainAnyElementsOf(FunctionWords.of(target));
    }

    @Test
    void attribution_file_namesTheSourceAndItsLicence() throws IOException {
        try (var stream = FunctionWords.class.getResourceAsStream("function-words/ATTRIBUTION")) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("stopwords-iso", "MIT", "Gene Diaz");
        }
    }

    @Test
    void sourceOnly_russianToUkrainian_isNotEmptyAndHoldsNoHomograph() {
        assertThat(FunctionWords.sourceOnly("ru", "uk"))
                .contains("что", "это")
                .doesNotContain("просто", "тебе", "через", "того", "себе", "перед");
    }
}
