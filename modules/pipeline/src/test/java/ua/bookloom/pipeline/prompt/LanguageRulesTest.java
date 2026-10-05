package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * The language-rules map: a prompt gets one section made from the target's rules, a few source notes and the pair's
 * rules, and the files of no other language are ever opened.
 */
class LanguageRulesTest {

    private static final String GENERIC = """
            status=untested
            quotes=GENERIC-QUOTES
            agreement=generic agreement
            pitfalls.1=generic pitfall
            example.1=Source: 1\\nReply: {"target":"1"}
            nameExample=generic name example
            names.policy.TRANSLATE=a
            names.policy.TRANSLITERATE=b
            names.policy.KEEP_ORIGINAL=c
            names.terms=d
            names.convention=e
            """;

    /** Opens an in-memory set of files and records every name it was asked for. */
    private static final class Files implements PromptTemplates.ResourceLoader {

        private final Map<String, String> contents;
        private final List<String> opened = new ArrayList<>();

        Files(final Map<String, String> contents) {
            this.contents = contents;
        }

        @Override
        public @Nullable InputStream open(final String fileName) {
            opened.add(fileName);
            final String text = contents.get(fileName);
            return text == null ? null : new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Files sentinelFiles() {
        return new Files(Map.of(
                "languages/generic.properties", GENERIC,
                "languages/en.properties", "status=tested\nquotes=EN-QUOTES\nsourceNotes.1=EN-SOURCE-NOTE",
                "languages/uk.properties", "status=tested\nquotes=UK-QUOTES\npitfalls.1=UK-PITFALL",
                "languages/fr.properties", "status=untested\nquotes=FR-QUOTES\nsourceNotes.1=FR-SOURCE-NOTE",
                "languages/pairs/en-uk.properties", "pitfalls.1=EN-UK-PAIR",
                "languages/pairs/fr-uk.properties", "pitfalls.1=FR-UK-PAIR",
                "languages/pairs/en-fr.properties", "pitfalls.1=EN-FR-PAIR"));
    }

    @Test
    void section_englishToUkrainian_isOneSectionOfTargetSourceAndPairRules() {
        final String section = new LanguageRules(sentinelFiles(), false).section("en", "uk", false);

        assertThat(section)
                .startsWith("[Language rules: English -> Ukrainian]\n")
                .contains("UK-QUOTES", "UK-PITFALL", "EN-SOURCE-NOTE", "EN-UK-PAIR")
                .containsOnlyOnce("[Language rules");
    }

    @Test
    void section_englishToUkrainian_opensOnlyThePairsOwnFiles() {
        final Files files = sentinelFiles();

        new LanguageRules(files, false).section("en", "uk", false);

        assertThat(files.opened)
                .containsExactlyInAnyOrder(
                        "languages/generic.properties",
                        "languages/uk.properties",
                        "languages/en.properties",
                        "languages/pairs/en-uk.properties");
    }

    @Test
    void section_englishToUkrainian_neverShowsAnotherLanguagesRules() {
        final String section = new LanguageRules(sentinelFiles(), false).section("en", "uk", true);

        assertThat(section).doesNotContain("FR-", "GENERIC-");
    }

    @Test
    void section_unknownTarget_getsTheGenericRules() {
        final String section = new LanguageRules(sentinelFiles(), false).section("en", "xx", false);

        assertThat(section)
                .contains("GENERIC-QUOTES", "generic pitfall", "EN-SOURCE-NOTE")
                .doesNotContain("UK-");
    }

    @Test
    void section_regionalAndScriptTags_useThePrimaryLanguageFile() {
        final LanguageRules rules = new LanguageRules(sentinelFiles(), false);

        assertThat(rules.section("en-GB", "uk-UA", false)).contains("UK-QUOTES", "EN-UK-PAIR");
    }

    @Test
    void section_unknownSource_leavesOutSourceNotesAndPair() {
        final String section = new LanguageRules(sentinelFiles(), false).section(null, "uk", false);

        assertThat(section)
                .startsWith("[Language rules: the source -> Ukrainian]\n")
                .contains("UK-QUOTES")
                .doesNotContain("EN-", "FR-");
    }

    @Test
    void section_genericOnly_ignoresEveryLanguageFile() {
        final Files files = sentinelFiles();

        final String section = new LanguageRules(files, true).section("en", "uk", false);

        assertThat(section).contains("GENERIC-QUOTES").doesNotContain("UK-", "EN-");
        assertThat(files.opened).containsExactly("languages/generic.properties");
    }

    @Test
    void section_reviewing_addsReviewerChecksOnly() {
        final LanguageRules rules = new LanguageRules(
                new Files(Map.of(
                        "languages/generic.properties",
                        GENERIC,
                        "languages/uk.properties",
                        "quotes=Q\nreviewerChecks.1=CHECK-ME")),
                false);

        assertThat(rules.section("en", "uk", true)).contains("Check: CHECK-ME");
        assertThat(rules.section("en", "uk", false)).doesNotContain("CHECK-ME");
    }

    @Test
    void section_twoCallsOfOnePair_areTheSameBytes() {
        final LanguageRules rules = new LanguageRules(sentinelFiles(), false);

        assertThat(rules.section("en", "uk", false)).isSameAs(rules.section("en", "uk", false));
    }

    @Test
    void load_genericWithoutARequiredKey_stopsLoading() {
        final Files files = new Files(Map.of("languages/generic.properties", "status=untested"));

        assertThatIllegalStateException()
                .isThrownBy(() -> new LanguageRules(files, false))
                .withMessageContaining("generic.properties")
                .withMessageContaining("quotes");
    }

    @Test
    void examples_pairBeforeTargetBeforeGeneric() {
        final LanguageRules rules = new LanguageRules(
                new Files(Map.of(
                        "languages/generic.properties", GENERIC,
                        "languages/uk.properties", "example.1=TARGET-EXAMPLE",
                        "languages/pairs/en-uk.properties", "example.1=PAIR-EXAMPLE")),
                false);

        assertThat(rules.examples("en", "uk")).isEqualTo("PAIR-EXAMPLE");
        assertThat(rules.examples("de", "uk")).isEqualTo("TARGET-EXAMPLE");
        assertThat(rules.examples("de", "ja")).startsWith("Source: 1\nReply:");
        assertThat(rules.examples(null, "uk")).isEqualTo("TARGET-EXAMPLE");
    }

    @Test
    void isTested_markedAndUnmarkedAndUnknown() {
        final LanguageRules rules = new LanguageRules(sentinelFiles(), false);

        assertThat(rules.hasTestedRules("uk")).isTrue();
        assertThat(rules.hasTestedRules("fr")).isFalse();
        assertThat(rules.hasTestedRules("xx")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"uk,tested", "en,tested", "ru,tested", "fr,untested", "de,untested", "pl,untested", "hr,untested"})
    void isTested_bundledLanguages_reportTheirStatus(final String tag, final String status) {
        assertThat(LanguageRules.bundled().hasTestedRules(tag)).isEqualTo("tested".equals(status));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ja", "zh-Hant", "und", "tlh", "x-private", ""})
    void section_anyOtherTag_stillWorksWithTheGenericRules(final String target) {
        final String section = LanguageRules.bundled().section("en", target, true);

        assertThat(section).contains("[Language rules: English -> ").contains("Quotes:");
        assertThat(section).doesNotContain("{{");
    }

    @Test
    void section_bundledEnglishToUkrainian_staysWithinTheSizeLimits() {
        final LanguageRules rules = LanguageRules.bundled();

        assertThat(tokens(rules.targetRules("uk", false))).isLessThanOrEqualTo(200);
        assertThat(tokens(rules.sourceRules("en"))).isLessThanOrEqualTo(80);
        assertThat(tokens(rules.pairRules("en", "uk", false))).isLessThanOrEqualTo(100);
    }

    private static int tokens(final String text) {
        return TokenEstimator.estimate(text, "en");
    }

    @Test
    void genderCheck_bundledLanguages_onlyUkrainianNamesOne() {
        final LanguageRules rules = LanguageRules.bundled();

        assertThat(rules.genderCheck("uk")).isEqualTo("uk");
        assertThat(rules.genderCheck("uk-UA")).isEqualTo("uk");
        assertThat(rules.genderCheck("en")).isNull();
        assertThat(rules.genderCheck("xx")).isNull();
    }
}
