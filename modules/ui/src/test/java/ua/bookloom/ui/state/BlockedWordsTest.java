package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.i18n.Messages;

/** Why a model action waits, in both display languages. */
class BlockedWordsTest {

    private static Messages messages(final String language) {
        return new Messages(() -> Locale.forLanguageTag(language));
    }

    // IF a provider check said "pause first" while a paused run already lets it run, THEN the person is misled.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en | BookLoom is translating. Try again when the run ends or while it is paused.",
                "uk | BookLoom перекладає. Спробуйте, коли переклад завершиться або поки його призупинено."
            })
    void why_translationBlocksProviderCheck_saysTryAgainWhilePaused(final String language, final String expected) {
        assertThat(BlockedWords.in(messages(language), ActivityKind.TRANSLATION, ActivityKind.PROVIDER_INFERENCE_TEST))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en | Pause the translation first — BookLoom sends one model request at a time",
                "uk | Спершу призупиніть переклад — BookLoom надсилає моделі один запит за раз"
            })
    void why_translationBlocksNamesPass_keepsPauseFirst(final String language, final String expected) {
        assertThat(BlockedWords.in(messages(language), ActivityKind.TRANSLATION, ActivityKind.NAMES_PASS))
                .isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void why_nonRunBlockerForProviderCheck_keepsTheGenericSentence() {
        assertThat(BlockedWords.in(messages("en"), ActivityKind.GLOSSARY_SCAN, ActivityKind.PROVIDER_INFERENCE_TEST))
                .startsWith("Unavailable while");
    }
}
