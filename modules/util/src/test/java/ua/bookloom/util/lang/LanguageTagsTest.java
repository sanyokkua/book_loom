package ua.bookloom.util.lang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageTagsTest {

    @ParameterizedTest
    @CsvSource({
        "EN,en",
        "en-US,en",
        "en_GB,en",
        "pt-BR,pt",
        "ua,uk",
        "UK,uk",
        "zh,zh-Hans",
        "zh-CN,zh-Hans",
        "zh-SG,zh-Hans",
        "zh_SG,zh-Hans",
        "zh-TW,zh-Hant",
        "zh-HK,zh-Hant",
        "zh-MO,zh-Hant",
        "zh-Hant-TW,zh-Hant",
        "nb-NO,nb",
        "' de ',de"
    })
    void normalize_recognizedTag_returnsCatalogueTag(String raw, String expected) {
        assertThat(LanguageTags.normalize(raw)).isPresent().hasValue(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"xx-yy", "xx", "ar", "he", "  "})
    void normalize_unrecognizedOrBlankTag_returnsEmpty(String raw) {
        assertThat(LanguageTags.normalize(raw)).isEmpty();
    }
}
