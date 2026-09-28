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

/** The target-script check — every spec scenario, the KEEP_ORIGINAL name removal and the foreign-marking edges. */
class ScriptCheckTest {

    private static final double FULL_MARGIN = 1.0;
    private static final double ZERO_MARGIN = 0.0;

    private static final SoftCheckInput CYRILLIC_PASSES = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "Він відчинив старі двері.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput SAME_SCRIPT_SKIPS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "Otworzył stare drzwi.",
            "en",
            "pl",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput SAME_SCRIPT_WRONG_LANGUAGE_SKIPS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "Otevřel staré dveře.",
            "en",
            "pl",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput SHORT_SOURCE_SKIPS = SoftCheckFixtures.scriptEcho(
            "Yes, sir.",
            "Так, сер.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput NAMES_KEPT_SHARE_PASSES = SoftCheckFixtures.scriptEcho(
            "Hale and Margaret Hale left Milton for London early that cold morning.",
            "Hale і Margaret Hale виїхали з Milton до London рано того холодного ранку.",
            "en",
            "uk",
            NamePolicy.KEEP_ORIGINAL,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of("Hale", "Margaret", "Milton", "London"));
    private static final SoftCheckInput UNCATALOGUED_TARGET_SKIPS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "Він відчинив старі двері.",
            "en",
            "xx",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput UNKNOWN_SOURCE_STILL_RUNS_PASSES = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "Він відчинив старі двері.",
            null,
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.KEEP,
            null,
            List.of());
    private static final SoftCheckInput NAMES_ONLY_NO_LETTERS_SKIPS = SoftCheckFixtures.scriptEcho(
            "Margaret Hale, Milton Northern.",
            "Margaret Hale, Milton Northern.",
            "en",
            "uk",
            NamePolicy.KEEP_ORIGINAL,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of("Margaret Hale", "Milton Northern"));
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
    private static final SoftCheckInput LATIN_TARGET_FAILS = SoftCheckFixtures.scriptEcho(
            "He opened the old door.",
            "Vin vidchynyv stari dveri.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of());
    private static final SoftCheckInput TRANSLITERATE_SHARE_FAILS = SoftCheckFixtures.scriptEcho(
            "Hale and Margaret Hale left Milton for London early that cold morning.",
            "Hale і Margaret Hale виїхали з Milton до London рано того холодного ранку.",
            "en",
            "uk",
            NamePolicy.TRANSLITERATE,
            ForeignPassagePolicy.TRANSLATE,
            null,
            List.of("Hale", "Margaret", "Milton", "London"));

    @ParameterizedTest(name = "{0}")
    @MethodSource("passOrSkipCases")
    void run_passOrSkip_matchesExpectedOutcome(
            final String name, final SoftCheckInput input, final boolean expectedSkipped) {
        final CheckResult result = ScriptCheck.run(input);

        assertThat(result.margin()).isCloseTo(FULL_MARGIN, within(1e-9));
        assertThat(result.passed()).isTrue();
        assertThat(result.skipped()).isEqualTo(expectedSkipped);
        assertThat(result.blocking()).isFalse();
        assertThat(result.finding()).isNull();
    }

    private static Stream<Arguments> passOrSkipCases() {
        return Stream.of(
                Arguments.of("Cyrillic target for English source passes", CYRILLIC_PASSES, false),
                Arguments.of("same-script pair skips the check", SAME_SCRIPT_SKIPS, true),
                Arguments.of("same-script wrong language is not caught", SAME_SCRIPT_WRONG_LANGUAGE_SKIPS, true),
                Arguments.of("short source skips the check", SHORT_SOURCE_SKIPS, true),
                Arguments.of("names kept by policy do not count", NAMES_KEPT_SHARE_PASSES, false),
                Arguments.of("uncatalogued target language skips the check", UNCATALOGUED_TARGET_SKIPS, true),
                Arguments.of("unknown source language still runs the check", UNKNOWN_SOURCE_STILL_RUNS_PASSES, false),
                Arguments.of(
                        "a names-only line kept by policy leaves the target with no letters",
                        NAMES_ONLY_NO_LETTERS_SKIPS,
                        true),
                Arguments.of("a marked French block is kept without failing", MARKED_FRENCH_SKIPS, true),
                Arguments.of("a Greek-script segment in an English book is kept", MARKED_GREEK_SKIPS, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failCases")
    void run_fail_blocksWithMediumLanguageFinding(final String name, final SoftCheckInput input) {
        final CheckResult result = ScriptCheck.run(input);

        assertThat(result.margin()).isCloseTo(ZERO_MARGIN, within(1e-9));
        assertThat(result.passed()).isFalse();
        assertThat(result.skipped()).isFalse();
        assertThat(result.blocking()).isTrue();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().kind()).isEqualTo("language");
        assertThat(result.finding().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.finding().raisedBy()).isEqualTo("script");
    }

    private static Stream<Arguments> failCases() {
        return Stream.of(
                Arguments.of("a Latin target for a Ukrainian book fails", LATIN_TARGET_FAILS),
                Arguments.of("under Transliterate the same target's share fails", TRANSLITERATE_SHARE_FAILS));
    }
}
