package ua.bookloom.llm.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ListKind;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelKind;
import ua.bookloom.llm.profile.ModelIdentityParser;

class LanguageAdvisorTest {

    private final ModelIdentityParser parser = new ModelIdentityParser();
    private final LanguageAdvisor bundled = new LanguageAdvisor(new ModelKnowledgeFiles());

    private ModelIdentity id(final String name) {
        return parser.parse(name).identity();
    }

    private static ModelFacts factsWithLanguages(final List<String> languages) {
        return ModelFacts.unknown().withLanguages(languages);
    }

    private static LanguageRow row(
            final String key, final MatchKind kind, final ListKind list, final Set<String> codes, final String ev) {
        return new LanguageRow(key, kind, key, list, codes, "test", ev);
    }

    private static LanguageAdvisor advisorOf(final LanguageRow... rows) {
        return new LanguageAdvisor(
                new KnowledgeTables("test", Map.of(), List.of(rows)), BaseKeyResolver.IDENTITY_BASE_FAMILY);
    }

    private static ModelIdentity family(final @Nullable String family, final @Nullable String baseFamily) {
        return new ModelIdentity(
                family, null, null, null, null, null, List.of(), ModelKind.LLM, baseFamily, null, null, List.of());
    }

    @ParameterizedTest
    @CsvSource({
        "llama3.1:8b, uk, NOT_DECLARED, ROW",
        "llama3.1:8b, ru, NOT_DECLARED, ROW",
        "llama3.1:8b, de, DECLARED, ROW",
        "mistral-small3.2, la, NOT_DECLARED, ROW",
        "mistral-small3.2, pl, DECLARED, ROW",
        "mistral-small3.2, uk, DECLARED, ROW",
        "qwen2.5:1.5b, zh-Hant, DECLARED, ROW",
        "qwen2.5:1.5b, uk, NOT_DECLARED, ROW",
        "qwen2.5:1.5b, ru, DECLARED, ROW",
        "mamaylm, ru, UNKNOWN, ROW",
        "mamaylm, uk, DECLARED, ROW",
        "gemma4:e4b-mlx, en, UNKNOWN, ROW",
        "gemma4:e4b-mlx, uk, UNKNOWN, ROW",
        "totally-unknown-model, uk, UNKNOWN, NONE"
    })
    void state_bundledTable_returnsExpected(
            final String name, final String tag, final LanguageState state, final LanguageSource source) {
        final LanguageResult result = bundled.state(id(name), null, tag);

        assertThat(result.state()).isEqualTo(state);
        assertThat(result.source()).isEqualTo(source);
    }

    @ParameterizedTest
    @CsvSource({
        "llama3.1:8b, llama-3.1",
        "gemma4:e4b-mlx, gemma4",
        "qwen2.5:1.5b, qwen2.5",
        "qwen3:8b, qwen3",
        "qwen3-vl:8b, qwen3",
        "phi4-mini, phi-4-mini",
        "deepseek-r1:7b, deepseek-r1",
        "mistral-small3.2, mistral-small-3.2",
        "mamaylm, mamaylm",
        "nvidia-nemotron-3.5-bolt-e4b, nemotron-3.5-bolt"
    })
    void state_parsedRealName_hitsExpectedRow(final String name, final String rowKey) {
        assertThat(bundled.state(id(name), null, "uk").rowKey()).isEqualTo(rowKey);
    }

    @Test
    void state_listNamingNo_declaresNb() {
        final LanguageAdvisor advisor =
                advisorOf(row("nordic", MatchKind.PREFIX, ListKind.COMPLETE, Set.of("no", "sv"), ""));

        assertThat(advisor.state(family("nordic", null), null, "nb").state()).isEqualTo(LanguageState.DECLARED);
        assertThat(advisor.state(family("nordic", null), null, "nn").state()).isEqualTo(LanguageState.DECLARED);
    }

    @Test
    void state_listNamingNb_declaresNo() {
        final LanguageAdvisor advisor = advisorOf(row("nordic", MatchKind.PREFIX, ListKind.COMPLETE, Set.of("nb"), ""));

        assertThat(advisor.state(family("nordic", null), null, "no").state()).isEqualTo(LanguageState.DECLARED);
    }

    @Test
    void state_factsListingOnlyEnglish_addNothing() {
        final ModelFacts facts = factsWithLanguages(List.of("en"));

        final LanguageResult result = bundled.state(id("gemma4:e4b-mlx"), facts, "en");

        assertThat(result.state()).isEqualTo(LanguageState.UNKNOWN);
        assertThat(result.source()).isEqualTo(LanguageSource.ROW);
    }

    @Test
    void state_factsListingUkOnNoneRow_isDeclaredFromFacts() {
        final ModelFacts facts = factsWithLanguages(List.of("en", "uk"));

        final LanguageResult result = bundled.state(id("gemma4:e4b-mlx"), facts, "uk");

        assertThat(result.state()).isEqualTo(LanguageState.DECLARED);
        assertThat(result.source()).isEqualTo(LanguageSource.FACTS);
    }

    @Test
    void state_factsListingUkOnCompleteRowWithoutIt_isDeclaredFromFacts() {
        final ModelFacts facts = factsWithLanguages(List.of("en", "uk"));

        final LanguageResult result = bundled.state(id("llama3.1:8b"), facts, "uk");

        assertThat(result.state()).isEqualTo(LanguageState.DECLARED);
        assertThat(result.source()).isEqualTo(LanguageSource.FACTS);
    }

    @Test
    void state_factsNotListingTag_keepRowVerdict() {
        final ModelFacts facts = factsWithLanguages(List.of("en", "de"));

        assertThat(bundled.state(id("llama3.1:8b"), facts, "uk").state()).isEqualTo(LanguageState.NOT_DECLARED);
    }

    @Test
    void state_fineTuneWithoutOwnRow_readsBaseRow() {
        final LanguageResult result = bundled.state(family("some-tune", "llama-3.1"), null, "uk");

        assertThat(result.state()).isEqualTo(LanguageState.NOT_DECLARED);
        assertThat(result.source()).isEqualTo(LanguageSource.BASE);
        assertThat(result.rowKey()).isEqualTo("llama-3.1");
    }

    @Test
    void state_modelWithOwnRow_ignoresBaseRow() {
        final LanguageResult result = bundled.state(family("mamaylm", "llama-3.1"), null, "uk");

        assertThat(result.source()).isEqualTo(LanguageSource.ROW);
        assertThat(result.state()).isEqualTo(LanguageState.DECLARED);
    }

    @Test
    void state_tablesCouldNotLoad_isUnknownFromNone() {
        final LanguageAdvisor broken = new LanguageAdvisor(null, BaseKeyResolver.IDENTITY_BASE_FAMILY);

        final LanguageResult result = broken.state(id("llama3.1:8b"), null, "uk");

        assertThat(result.state()).isEqualTo(LanguageState.UNKNOWN);
        assertThat(result.source()).isEqualTo(LanguageSource.NONE);
    }

    @Test
    void state_longestPrefixWins_qwen3VlOverQwen3() {
        final LanguageAdvisor advisor = advisorOf(
                row("qwen3", MatchKind.PREFIX, ListKind.COMPLETE, Set.of("en"), ""),
                row("qwen3-vl", MatchKind.PREFIX, ListKind.COMPLETE, Set.of("uk"), ""));

        assertThat(advisor.state(family("qwen3-vl", null), null, "uk").rowKey()).isEqualTo("qwen3-vl");
        assertThat(advisor.state(family("qwen3", null), null, "uk").rowKey()).isEqualTo("qwen3");
    }

    @Test
    void state_exactBeatsPrefix_boltOverNemotron3() {
        final LanguageAdvisor advisor = advisorOf(
                row("nemotron3", MatchKind.PREFIX, ListKind.COMPLETE, Set.of("en"), ""),
                row("nemotron-3-bolt", MatchKind.EXACT, ListKind.NONE, Set.of(), ""));

        final ModelIdentity bolt = new ModelIdentity(
                "nemotron", "3", "bolt", null, null, null, List.of(), ModelKind.LLM, null, null, null, List.of());

        assertThat(advisor.state(bolt, null, "en").rowKey()).isEqualTo("nemotron-3-bolt");
    }

    @Test
    void state_exactRowDoesNotMatchLongerName() {
        final LanguageAdvisor advisor = advisorOf(row("muse", MatchKind.EXACT, ListKind.NONE, Set.of(), ""));

        assertThat(advisor.state(family("muse-glimmer", null), null, "uk").source())
                .isEqualTo(LanguageSource.NONE);
    }

    @Test
    void testedPair_mamayLmRuToUk_isTrueFromItsOwnRow() {
        assertThat(bundled.testedPair(id("mamaylm"), "ru", "uk")).isTrue();
    }

    @Test
    void testedPair_otherDirectionOrPair_isFalse() {
        assertThat(bundled.testedPair(id("mamaylm"), "uk", "ru")).isFalse();
        assertThat(bundled.testedPair(id("mamaylm"), "de", "uk")).isFalse();
    }

    @Test
    void testedPair_fineTuneOfMamayLm_isFalse() {
        assertThat(bundled.testedPair(family("some-tune", "mamaylm"), "ru", "uk"))
                .isFalse();
    }

    @Test
    void testedPair_regionTagsCompareByGroup() {
        assertThat(bundled.testedPair(id("mamaylm"), "ru-RU", "uk-UA")).isTrue();
    }
}
