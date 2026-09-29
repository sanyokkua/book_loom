package ua.bookloom.util.lang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LanguagesTest {

    @Test
    void all_noArguments_holdsExactly34DistinctTags() {
        final Set<String> tags =
                Languages.all().stream().map(Language::tag).collect(Collectors.toCollection(HashSet::new));
        assertThat(Languages.all()).hasSize(34);
        assertThat(tags).hasSize(34);
    }

    @Test
    void byTag_nb_hasNorwegianBokmalDisplayName() {
        assertThat(Languages.byTag("nb"))
                .isPresent()
                .hasValueSatisfying(
                        language -> assertThat(language.displayName()).isEqualTo("Norwegian Bokmål"));
    }

    @Test
    void byTag_zhHant_hasChineseTraditionalDisplayName() {
        assertThat(Languages.byTag("zh-Hant"))
                .isPresent()
                .hasValueSatisfying(
                        language -> assertThat(language.displayName()).isEqualTo("Chinese (Traditional)"));
    }

    @Test
    void byTag_ga_hasIrishDisplayName() {
        assertThat(Languages.byTag("ga"))
                .isPresent()
                .hasValueSatisfying(
                        language -> assertThat(language.displayName()).isEqualTo("Irish"));
    }

    @ParameterizedTest
    @CsvSource({"uk,CYRILLIC,3.0", "el,GREEK,3.5", "ja,JAPANESE,1.5", "ko,HANGUL,1.5", "en,LATIN,4.0", "sr,CYRILLIC,3.0"
    })
    void byTag_catalogedTag_hasExpectedScriptAndFigure(String tag, Script expectedScript, double expectedFigure) {
        assertThat(Languages.byTag(tag)).isPresent().hasValueSatisfying(language -> {
            assertThat(language.script()).isEqualTo(expectedScript);
            assertThat(language.script().charsPerToken()).isEqualTo(expectedFigure);
        });
    }

    @Test
    void byTag_unnormalizedTagWithRegion_isEmpty() {
        assertThat(Languages.byTag("en-US")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"uk,CYRILLIC", "EN,LATIN", "en-US,LATIN", "zh-TW,HAN", "ja,JAPANESE"})
    void scriptOf_catalogedTag_returnsItsScript(String tag, Script expected) {
        assertThat(Languages.scriptOf(tag)).isPresent().hasValue(expected);
    }

    @Test
    void scriptOf_null_isEmpty() {
        assertThat(Languages.scriptOf(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"la", "haw", "ar"})
    void scriptOf_uncataloguedLanguage_isEmpty(String tag) {
        assertThat(Languages.scriptOf(tag)).isEmpty();
    }

    @Test
    void scriptOf_unrecognizedTag_isEmpty() {
        assertThat(Languages.scriptOf("xx")).isEmpty();
    }
}
