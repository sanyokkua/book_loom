package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The machine codes the pipeline reports — a call's kind, the call a refused setting covers — read as words of the
 * interface language, never as the raw code; a code no catalogue knows yet is shown as it is rather than dropped.
 */
class CodeWordsTest {

    private static Messages messagesFor(final String tag) {
        return new Messages(new FixedLocaleProvider(Locale.forLanguageTag(tag)));
    }

    static Stream<Arguments> callKindsInEachLanguage() {
        return Stream.of(CallKind.values())
                .flatMap(kind -> Stream.of("en", "uk").map(tag -> Arguments.of(kind, tag)));
    }

    // IF a call kind had no entry, THEN the log and the live panel would say only "Model call" for it.
    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("callKindsInEachLanguage")
    void callKind_everyKind_hasItsOwnWords(final CallKind kind, final String tag) {
        final Messages messages = messagesFor(tag);
        final String unknown = messages.get(MessageKey.RUN_CALL_KIND, "other");

        assertThat(messages.get(MessageKey.RUN_CALL_KIND, kind.name().toLowerCase(Locale.ROOT)))
                .isNotEqualTo(unknown);
    }

    // 15i.N7: the recurring-term choice was labelled "Name review"; it has its own words in both languages.
    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource({
        "term_choice, en, Term choice",
        "term_choice, uk, Вибір термінів",
        "review_terms, en, Name review",
        "review_terms, uk, Перевірка імен",
    })
    void callKind_termChoiceAndNameReview_readAsDifferentWords(
            final String kind, final String tag, final String expected) {
        assertThat(messagesFor(tag).get(MessageKey.RUN_CALL_KIND, kind)).isEqualTo(expected);
    }

    // IF a code no catalogue knows were dropped or turned into "other", THEN a new scope would vanish from the screen.
    @ParameterizedTest
    @CsvSource({"uk, BRAND_NEW, Лише: BRAND_NEW", "en, made-up, Only: made-up"})
    void code_unknown_isShownAsItIs(final String tag, final String code, final String expected) {
        assertThat(messagesFor(tag).code(MessageKey.SETTINGS_MODELS_REFUSED_SCOPE, code))
                .isEqualTo(expected);
    }
}
