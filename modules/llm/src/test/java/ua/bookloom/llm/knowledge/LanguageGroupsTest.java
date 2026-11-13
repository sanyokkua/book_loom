package ua.bookloom.llm.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LanguageGroupsTest {

    @ParameterizedTest
    @CsvSource({
        "uk, uk",
        "UK, uk",
        "zh-Hant, zh",
        "zh-Hans, zh",
        "pt-BR, pt",
        "iw, he",
        "nb, no",
        "nn, no",
        "no, no",
        "nb-NO, no",
        "en_US, en"
    })
    void groupOf_tag_returnsPrimarySubtagGroup(final String tag, final String expected) {
        assertThat(LanguageGroups.groupOf(tag)).isEqualTo(expected);
    }
}
