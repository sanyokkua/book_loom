package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.DisplayText;

/** The untranslated-echo check — the echo floor, the KEEP_ORIGINAL name removal and the foreign-marking edges. */
class EchoCheckTest {

    private static final double FULL_MARGIN = 1.0;
    private static final double ZERO_MARGIN = 0.0;

    private static final SoftCheckInput REAL_TRANSLATION_PASSES = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "Він відчинив старі двері.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    // A source of protected tokens only display-texts to empty, exactly like a real all-placeholder segment would.
    private static final SoftCheckInput EMPTY_SOURCE_SKIPS = SoftCheckFixtures.scriptEcho(
            DisplayText.of("⟦g0⟧⟦g1⟧"),
            DisplayText.of("⟦g0⟧⟦g1⟧"),
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput MARKED_FRENCH_SKIPS = SoftCheckFixtures.scriptEcho(
            "Je ne regrette rien.",
            "Je ne regrette rien.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.KEEP,
            "fr",
            List.of());
    private static final SoftCheckInput MARKED_GREEK_SKIPS = SoftCheckFixtures.scriptEcho(
            "Γνῶθι σεαυτόν",
            "Γνῶθι σεαυτόν",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.KEEP,
            null,
            List.of());
    private static final SoftCheckInput VERBATIM_COPY_FAILS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "He opened the old door.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput CAPITALS_COPY_FAILS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "HE OPENED THE OLD DOOR.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput EXACTLY_TWENTY_BLOCKS = SoftCheckFixtures.scriptEcho(
            "It was a dark night.",
            "IT WAS A DARK NIGHT.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput BELOW_FLOOR_NOT_BLOCKING = SoftCheckFixtures.scriptEcho(
            "Yes, sir.",
            "YES, SIR.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput NAMES_ONLY_NOT_BLOCKING = SoftCheckFixtures.scriptEcho(
            "Margaret Hale, Milton Northern.",
            "Margaret Hale, Milton Northern.",
            "en",
            "uk",
            NamePolicy.KEEP_ORIGINAL,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of("Margaret Hale", "Milton Northern"));
    private static final SoftCheckInput CYRILLIC_NAMES_ONLY_NOT_BLOCKING = SoftCheckFixtures.scriptEcho(
            "Тарас Бульба, Остап Бульба.",
            "Тарас Бульба, Остап Бульба.",
            "uk",
            "en",
            NamePolicy.KEEP_ORIGINAL,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of("Тарас Бульба", "Остап Бульба"));
    private static final SoftCheckInput TRANSLATE_POLICY_MARKED_BLOCK_FAILS = SoftCheckFixtures.scriptEcho(
            "Je ne regrette rien.",
            "Je ne regrette rien.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            "fr",
            List.of());
    private static final SoftCheckInput UNMARKED_KEEP_FAILS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "He opened the old door.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.KEEP,
            null,
            List.of());
    private static final SoftCheckInput REGION_VARIANT_NOT_MARKED_FAILS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "He opened the old door.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.KEEP,
            "en-GB",
            List.of());

    @ParameterizedTest(name = "{0}")
    @MethodSource("passOrSkipCases")
    void run_passOrSkip_matchesExpectedOutcome(
            final String name, final SoftCheckInput input, final boolean expectedSkipped) {
        final CheckResult result = EchoCheck.run(input);

        assertThat(result.margin()).isCloseTo(FULL_MARGIN, within(1e-9));
        assertThat(result.passed()).isTrue();
        assertThat(result.skipped()).isEqualTo(expectedSkipped);
        assertThat(result.blocking()).isFalse();
        assertThat(result.finding()).isNull();
    }

    private static Stream<Arguments> passOrSkipCases() {
        return Stream.of(
                Arguments.of("a real translation passes", REAL_TRANSLATION_PASSES, false),
                Arguments.of("an empty source display text skips the check", EMPTY_SOURCE_SKIPS, true),
                Arguments.of("a marked French block is kept without failing", MARKED_FRENCH_SKIPS, true),
                Arguments.of("a Greek-script segment in an English book is kept", MARKED_GREEK_SKIPS, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failCases")
    void run_fail_recordsFindingAndBlockingPerFloor(
            final String name,
            final SoftCheckInput input,
            final boolean expectedBlocking,
            final Severity expectedSeverity) {
        final CheckResult result = EchoCheck.run(input);

        assertThat(result.margin()).isCloseTo(ZERO_MARGIN, within(1e-9));
        assertThat(result.passed()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.blocking()).isEqualTo(expectedBlocking);
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().kind()).isEqualTo("language");
        assertThat(result.finding().severity()).isEqualTo(expectedSeverity);
        assertThat(result.finding().raisedBy()).isEqualTo("echo");
    }

    private static Stream<Arguments> failCases() {
        return Stream.of(
                Arguments.of("a verbatim copy fails", VERBATIM_COPY_FAILS, true, Severity.MEDIUM),
                Arguments.of("a copy in capitals still fails", CAPITALS_COPY_FAILS, true, Severity.MEDIUM),
                Arguments.of("exactly twenty code points still blocks", EXACTLY_TWENTY_BLOCKS, true, Severity.MEDIUM),
                Arguments.of(
                        "below the echo floor only lowers confidence", BELOW_FLOOR_NOT_BLOCKING, false, Severity.LOW),
                Arguments.of("a names-only line kept by policy", NAMES_ONLY_NOT_BLOCKING, false, Severity.LOW),
                Arguments.of("Cyrillic names kept by policy", CYRILLIC_NAMES_ONLY_NOT_BLOCKING, false, Severity.LOW),
                Arguments.of(
                        "Translate checks a marked block", TRANSLATE_POLICY_MARKED_BLOCK_FAILS, true, Severity.MEDIUM),
                Arguments.of("an unmarked echo fails under Keep as-is", UNMARKED_KEEP_FAILS, true, Severity.MEDIUM),
                Arguments.of(
                        "a source-language region is not marked",
                        REGION_VARIANT_NOT_MARKED_FAILS,
                        true,
                        Severity.MEDIUM));
    }
}
