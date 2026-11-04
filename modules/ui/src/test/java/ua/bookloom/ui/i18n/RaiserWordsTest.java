package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Every check and every other raiser of a finding reads as words of the window's language, never as a code. */
class RaiserWordsTest {

    private static Messages messagesFor(final String tag) {
        return new Messages(new FixedLocaleProvider(Locale.forLanguageTag(tag)));
    }

    // The raisedBy of every ua.bookloom.pipeline.qa.CheckName (not exported to :ui, so listed here).
    private static final List<String> CHECKS = List.of(
            "script",
            "echo",
            "repetition",
            "length",
            "glossary",
            "refusal",
            "placeholder",
            "locked-term",
            "kept-run",
            "script-purity",
            "quote-balance",
            "language-identity",
            "sentence-count",
            "vocative",
            "protocol-leak",
            "name-swap",
            "duplicate-word",
            "spacing",
            "gender",
            "name-gender",
            "identifier",
            "number",
            "latin-run",
            "name-missing",
            "name-spelling",
            "function-word-residue",
            "address-mix",
            "unknown-word",
            "alphabet",
            "normalised",
            "terminal-mark",
            "address-parity");

    static Stream<Arguments> everyCheckInEachLanguage() {
        return CHECKS.stream().flatMap(check -> Stream.of("en", "uk").map(tag -> Arguments.of(check, tag)));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCheckInEachLanguage")
    void raiser_everyCheckName_hasItsOwnWords(final String raisedBy, final String tag) {
        final Messages messages = messagesFor(tag);
        final String unknown = RaiserWords.raiser(messages, "no-such-check");

        assertThat(RaiserWords.raiser(messages, raisedBy)).isNotEqualTo(unknown);
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCheckInEachLanguage")
    void raiser_auditPrefixedCheckName_hasItsOwnWords(final String raisedBy, final String tag) {
        final Messages messages = messagesFor(tag);
        final String unknown = RaiserWords.raiser(messages, "no-such-check");

        assertThat(RaiserWords.raiser(messages, RaiserWords.checkOf("audit:" + raisedBy)))
                .isNotEqualTo(unknown);
    }

    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource({
        "negation, en",
        "token-leak, en",
        "source-variants, en",
        "name-variants, en",
        "reply, en",
        "reply-format, en",
        "reviewer-unavailable, en",
        "negation, uk",
        "token-leak, uk",
        "source-variants, uk",
        "name-variants, uk",
        "reply, uk",
        "reply-format, uk",
        "reviewer-unavailable, uk",
    })
    void raiser_nonCheckRaisers_haveWords(final String raisedBy, final String tag) {
        final Messages messages = messagesFor(tag);

        assertThat(RaiserWords.raiser(messages, raisedBy)).isNotEqualTo(RaiserWords.raiser(messages, "no-such-check"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"audit:negation", "negation"})
    void checkOf_prefixOrNot_givesTheCheck(final String raisedBy) {
        assertThat(RaiserWords.checkOf(raisedBy)).isEqualTo("negation");
    }

    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource({
        "audit:negation, en, negation",
        "audit:terminal-mark, en, terminal mark",
        "audit:source-variants, en, source variants",
        "audit:name-variants, en, name variants",
        "audit:token-leak, en, leaked placeholder",
        "audit:function-word-residue, en, untranslated word",
        "audit:normalised, en, typography",
        "audit:negation, uk, заперечення",
        "audit:normalised, uk, типографіка",
        "audit:somethingnew, en, doubt",
    })
    void doubt_auditFinding_readsAsShortNoun(final String raisedBy, final String tag, final String expected) {
        assertThat(RaiserWords.doubt(messagesFor(tag), RaiserWords.checkOf(raisedBy)))
                .isEqualTo(expected);
    }
}
