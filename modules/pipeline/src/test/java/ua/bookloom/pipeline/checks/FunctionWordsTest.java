package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FunctionWordsTest {

    @Test
    void of_missingLanguageFile_isEmpty() {
        assertThat(FunctionWords.of("xx")).isEmpty();
    }

    @Test
    void sourceOnly_missingLanguageFile_isEmpty() {
        assertThat(FunctionWords.sourceOnly("xx", "uk")).isEmpty();
    }

    @Test
    void sourceOnly_languageTagWithRegion_usesThePrimarySubtag() {
        assertThat(FunctionWords.sourceOnly("ru-RU", "uk-UA")).isEqualTo(FunctionWords.sourceOnly("ru", "uk"));
    }

    @Test
    void parse_commentsAndBlankLines_areIgnoredAndWordsLowerCased() {
        assertThat(FunctionWords.parse("# note\n\nЧто\n  и  \n")).containsExactlyInAnyOrder("что", "и");
    }
}
