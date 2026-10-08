package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.CallKind;

/**
 * The machine codes the pipeline reports — a call's kind, why a batch item fell back or a segment was flagged, the
 * rule a consistency answer broke — read as words of the interface language, never as the raw code; a code no
 * catalogue knows yet is shown as it is rather than dropped.
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

    // IF an outcome's reason were shown as its code, THEN a person would read MISSING or reviewer-unavailable.
    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource({
        "MISSING, en, missing from the reply",
        "MERGED_SUSPECT, en, merged with another",
        "TOO_LONG, en, too long",
        "QUOTES, en, quote marks not paired",
        "GATE, en, refused by the checks",
        "reviewer-unavailable, en, reviewer unavailable",
        "omission, en, something left out",
        "MISSING, uk, немає у відповіді",
        "reviewer-unavailable, uk, рецензент недоступний",
    })
    void outcomeDetail_knownCode_readsAsWords(final String code, final String tag, final String expected) {
        assertThat(messagesFor(tag).code(MessageKey.LIVE_CALL_DETAIL, code)).isEqualTo(expected);
    }

    // IF a rule a consistency answer broke were shown as its code, THEN the result line would read "latin-run: 1".
    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource({
        "quotes, en, quote marks",
        "latin-run, en, English words left",
        "not-accepted, en, not accepted",
        "quotes, uk, лапки",
        "worse, uk, гірша оцінка",
    })
    void consistencyRule_knownRule_readsAsWords(final String rule, final String tag, final String expected) {
        assertThat(messagesFor(tag).code(MessageKey.EXPORT_CHECK_CONSISTENCY_RULE, rule))
                .isEqualTo(expected);
    }

    // IF a code no catalogue knows were dropped or turned into "other", THEN a new reason would vanish from the screen.
    @ParameterizedTest
    @ValueSource(strings = {"BRAND_NEW", "made-up"})
    void code_unknown_isShownAsItIs(final String code) {
        assertThat(messagesFor("uk").code(MessageKey.LIVE_CALL_DETAIL, code)).isEqualTo(code);
        assertThat(messagesFor("en").code(MessageKey.EXPORT_CHECK_CONSISTENCY_RULE, code))
                .isEqualTo(code);
    }
}
