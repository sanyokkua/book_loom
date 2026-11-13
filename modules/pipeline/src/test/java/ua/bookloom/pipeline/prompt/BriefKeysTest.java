package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.BriefChoices;
import ua.bookloom.api.project.QuoteStyle;

/** The Book Brief's choices come from the target language's own file, and only for the keys it sets. */
class BriefKeysTest {

    @Test
    void briefChoices_ukrainian_offersApostropheQuotesAddressAndHardG() {
        final BriefChoices choices = LanguageRules.bundled().briefChoices("uk");

        assertThat(choices.apostrophes()).containsExactly("’", "ʼ", "'");
        assertThat(choices.quoteStyles())
                .containsExactly(new QuoteStyle("«", "»", "„", "“"), new QuoteStyle("„", "“", "‚", "‘"));
        assertThat(choices.address()).isEqualTo(new BriefChoices.AddressWords("ти", "Ви"));
        assertThat(choices.conventions())
                .containsExactly(new BriefChoices.DeclaredConvention("hardG", List.of("г", "ґ"), "г"));
    }

    @Test
    void briefChoices_russian_offersApostropheQuotesAndAddressButNoConvention() {
        final BriefChoices choices = LanguageRules.bundled().briefChoices("ru-RU");

        assertThat(choices.apostrophes()).containsExactly("’", "'");
        assertThat(choices.quoteStyles())
                .containsExactly(new QuoteStyle("«", "»", "„", "“"), new QuoteStyle("«", "»", "“", "”"));
        assertThat(choices.address()).isEqualTo(new BriefChoices.AddressWords("ты", "Вы"));
        assertThat(choices.conventions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "xx", ""})
    void briefChoices_languageSettingNoKey_offersNone(final String tag) {
        assertThat(LanguageRules.bundled().briefChoices(tag)).isEqualTo(BriefChoices.NONE);
    }

    @Test
    void read_malformedQuoteStyleAndConvention_areLeftOut() {
        final LanguageFile file = new LanguageFile(
                "zz",
                java.util.Map.of(
                        "briefQuotes", "«»„“ «»",
                        "conventions", "hardG broken",
                        "convention.hardG.choices", "г ґ",
                        "convention.hardG.default", "г",
                        "convention.broken.choices", "a"));

        final BriefChoices choices = BriefKeys.read(file);

        assertThat(choices.quoteStyles()).containsExactly(new QuoteStyle("«", "»", "„", "“"));
        assertThat(choices.conventions())
                .extracting(BriefChoices.DeclaredConvention::key)
                .containsExactly("hardG");
    }
}
