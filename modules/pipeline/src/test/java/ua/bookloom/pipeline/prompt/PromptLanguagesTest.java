package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A prompt names any language the JDK can name, catalogued or not. */
class PromptLanguagesTest {

    @ParameterizedTest
    @CsvSource({"en,English (en)", "uk,Ukrainian (uk)", "la,Latin (la)", "haw,Hawaiian (haw)"})
    void describe_languageTheJdkNames_returnsNameAndTag(String tag, String expected) {
        assertThat(PromptLanguages.describe(tag)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"xx,language tag \"xx\""})
    void describe_tagNoLanguageNames_returnsQuotedTag(String tag, String expected) {
        assertThat(PromptLanguages.describe(tag)).isEqualTo(expected);
    }
}
