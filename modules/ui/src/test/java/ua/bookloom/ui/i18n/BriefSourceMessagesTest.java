package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The words of the Book Brief's Auto source and the model's language proposal, in both shipped languages (15i.U4). */
class BriefSourceMessagesTest {

    // IF a bundle lacked the Auto entry, the proposal or its button, THEN that language would show a key or English.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en | brief.source.auto | Find out from the text",
                "uk | brief.source.auto | Визначити за текстом",
                "en | brief.source.proposal | Looks like {0}",
                "uk | brief.source.proposal | Схоже, це {0}",
                "en | brief.source.use | Use it",
                "uk | brief.source.use | Використати",
            })
    void sourceKeys_autoEntryAndProposal_areWordedInBothLanguages(
            final String language, final String key, final String expected) {
        assertThat(Catalogues.load(language).get(key)).isEqualTo(expected);
    }

    // IF the hover of "Use it" were missing, THEN the one button that changes the source would explain nothing.
    @ParameterizedTest
    @CsvSource({"en", "uk"})
    void useItTip_bothLanguages_isNonBlank(final String language) {
        assertThat(Catalogues.load(language).get("brief.source.use.tip")).isNotBlank();
    }

    // IF the hover did not say what is set, THEN a person would not know "Use it" changes the source language only.
    @Test
    void useItTip_english_namesTheSourceLanguage() {
        assertThat(Catalogues.load("en").get("brief.source.use.tip")).contains("source language");
    }

    // IF the Auto note kept the claim that the book declares nothing, THEN it would be false after the person chose
    // Auto.
    @ParameterizedTest
    @CsvSource({"en", "uk"})
    void autoNote_bothLanguages_doesNotClaimTheBookDeclaresNoLanguage(final String language) {
        final String note = Catalogues.load(language).get("brief.source.undeclared");

        assertThat(note).isNotBlank().doesNotContain("does not declare").doesNotContain("не зазначає");
    }
}
