package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The per-target name properties of the language files (15i.N6): whether the target declines names, and the letter
 * table a transliteration is checked against. Both are data, so a language is added without code.
 */
class LanguageNameRulesTest {

    private static final LanguageRules RULES = LanguageRules.bundled();

    @ParameterizedTest
    @ValueSource(strings = {"uk", "ru", "pl", "cs", "hr", "sk", "sl", "xx", "zz-ZZ"})
    void namesInflect_inflectingOrUnknownTarget_isTrue(final String target) {
        assertThat(RULES.namesInflect(target)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "fr", "es", "pt"})
    void namesInflect_languageThatKeepsNamesWhole_isFalse(final String target) {
        assertThat(RULES.namesInflect(target)).isFalse();
    }

    // Every shipped language states the property outright, so the default never decides for a language we ship.
    @ParameterizedTest
    @MethodSource("ua.bookloom.pipeline.prompt.V1Languages#languages")
    void languageFile_everyV1Language_statesNamesInflect(final String tag) throws IOException {
        assertThat(LanguageFilesTest.file("languages/" + tag + ".properties").getProperty("namesInflect"))
                .as(tag)
                .isIn("true", "false");
    }

    @Test
    void translit_ukrainian_mapsOpeningLettersAndDigraphs() {
        assertThat(RULES.translit("uk"))
                .containsKeys("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m")
                .containsKeys("n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z")
                .containsKeys("sh", "ch", "th", "ph", "kh", "zh");
        assertThat(RULES.translit("uk").get("a")).contains("а", "е");
        assertThat(RULES.translit("uk").get("sh")).containsExactly("ш");
        assertThat(RULES.translit("uk").get("th")).containsExactly("т");
        assertThat(RULES.translit("uk").get("ph")).containsExactly("ф");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "xx"})
    void translit_targetWithNoTable_isEmpty(final String target) {
        assertThat(RULES.translit(target)).isEmpty();
    }
}
