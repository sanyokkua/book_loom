package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelIdentity;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ServerEngine;

/** The built-in rows: every pattern reads the parser's output, no two rows speak for one name, owners' models land. */
class BuiltInRowsTest {

    private static final String OWNER_IDS = "/ua/bookloom/llm/identity/owner-installed-ids.csv";
    private static final String CORPUS_V1 = "/ua/bookloom/llm/identity/name-cases-v1.csv";
    private static final String CORPUS_V2 = "/ua/bookloom/llm/identity/name-cases-v2.csv";
    private static final String NO_ROW = "none";

    private final BuiltInRows builtIn = new BuiltInRows();
    private final ModelIdentityParser parser = new ModelIdentityParser();
    private final ModelProfiles profiles = new ModelProfiles(builtIn);

    @Test
    void rows_everyPattern_parses() {
        assertThat(builtIn.rows()).isNotEmpty().allSatisfy(row -> {
            assertThat(row.examples()).as(row.row().name()).isNotEmpty();
            assertThat(row.examples()).allSatisfy(example -> {
                final ModelIdentity identity = parser.parse(example).identity();
                assertThat(identity.family())
                        .as(example)
                        .isEqualTo(row.pattern().family());
                assertThat(row.pattern().matches(identity)).as(example).isTrue();
            });
        });
        assertThat(builtIn.rows().stream().map(r -> r.row().name())).doesNotHaveDuplicates();
    }

    @Test
    void rows_noTwoRowsShadowEachOther() {
        assertThat(builtIn.rows())
                .allSatisfy(row -> assertThat(row.examples()).allSatisfy(example -> {
                    final ModelIdentity identity = parser.parse(example).identity();
                    final List<String> matching = builtIn.rows().stream()
                            .filter(other -> other.pattern().matches(identity))
                            .map(other -> other.row().name())
                            .toList();
                    assertThat(matching).as(example).containsExactly(row.row().name());
                }));
    }

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = CORPUS_V1, numLinesToSkip = 1, quoteCharacter = '"', maxCharsPerColumn = 4096)
    void rows_corpusV1Name_isSpokenForByAtMostOneRow(
            final String id,
            final String input,
            final String inputKind,
            final String provenance,
            final String expectedJson,
            final @Nullable String mustBeAbsent,
            final String resolvedBy,
            final @Nullable String note) {
        assertThat(rowsMatching(input)).as(id).hasSizeLessThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = CORPUS_V2, numLinesToSkip = 1, quoteCharacter = '"', maxCharsPerColumn = 4096)
    void rows_corpusV2Name_isSpokenForByAtMostOneRow(
            final String id,
            final String input,
            final String inputKind,
            final String provenance,
            final String expectedJson,
            final @Nullable String mustBeAbsent,
            final String resolvedBy,
            final @Nullable String note) {
        assertThat(rowsMatching(input)).as(id).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void resolve_ministralReasoning_isNotTheInstructRow() {
        final ResolvedProfile reasoning = resolve("mistralai/ministral-3-14b-reasoning", ModelFacts.unknown());
        final ResolvedProfile instruct = resolve("ministral-3:14b", ModelFacts.unknown());

        assertThat(reasoning.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.ALWAYS);
        assertThat(reasoning.profile().reasoning().offValue()).isNull();
        assertThat(rowName("mistralai/ministral-3-14b-reasoning")).isEqualTo("ministral-3-reasoning");
        assertThat(instruct.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.NONE);
        assertThat(rowName("ministral-3:14b")).isEqualTo("ministral-3-14b");
    }

    @ParameterizedTest
    @CsvSource({"llama", "deepseek2", "gemma4", "qwen35"})
    void resolve_architectureAlone_selectsNoRow(final String architecture) {
        final ModelFacts facts = factsOf(architecture);

        final Optional<ProfileRows.Picked> picked = builtIn.pick(
                parser.parse("hf.co/someuser/Velemir-Writer-12B-GGUF:Q4_K_M").identity(), facts);

        assertThat(picked).isEmpty();
    }

    @Test
    void resolve_llamaArchitectureAlone_selectsNoRow() {
        final ResolvedProfile resolved = resolve("hf.co/someuser/Velemir-Writer-12B-GGUF:Q4_K_M", factsOf("llama"));

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.DEFAULT);
        assertThat(resolved.verdict()).isEqualTo("unknown model");
    }

    @Test
    void resolve_deepseek2ArchitectureAlone_selectsNoRow() {
        final ResolvedProfile resolved = resolve("hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL", factsOf("deepseek2"));

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.DEFAULT);
        assertThat(resolved.profile().reasoning()).isEqualTo(ReasoningControl.NONE);
    }

    @Test
    void resolve_nameOfAnotherFamily_winsOverTheReportedArchitecture() {
        assertThat(rowName("hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL"))
                .isEqualTo("mistral-small-3.2-24b");
        assertThat(resolve("hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL", factsOf("llama"))
                        .match())
                .isEqualTo(ModelReport.Match.EXACT);
    }

    @Test
    void resolve_boltNameWithNemotronArchitecture_isTheDefaultProfile() {
        final ResolvedProfile resolved = resolve("nvidia-nemotron-3.5-bolt-e4b", factsOf("nemotron_h"));

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.DEFAULT);
        assertThat(resolved.verdict()).isEqualTo("unknown model");
        assertThat(resolved.identity().family()).isEqualTo("nemotron-bolt");
        assertThat(resolved.profile().reasoning()).isEqualTo(ReasoningControl.NONE);
        assertThat(resolved.profile().batchSize()).isEqualTo(2);
    }

    @Test
    void resolve_gptOss20b_isMixtureOfExpertsByItsActiveSize() {
        final ResolvedProfile resolved = resolve("gpt-oss:20b", ModelFacts.unknown());

        assertThat(resolved.identity().activeB()).isEqualTo(3.6);
        assertThat(rowName("gpt-oss:20b")).isEqualTo("gpt-oss-20b");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "hf.co/bartowski/utter-project_EuroLLM-22B-Instruct-2512-GGUF:IQ4_NL, eurollm, NONE,",
        "hf.co/unsloth/Mistral-Small-3.2-24B-Instruct-2506-GGUF:IQ4_NL, mistral-small-3.2-24b, NONE,",
        "qwen3.5:9b, qwen3.5-9b, BOOLEAN, false",
        "gpt-oss:20b, gpt-oss-20b, ALWAYS, low",
        "qwen2.5:1.5b, qwen2.5-small, NONE,",
        "gemma4:26b-mlx, gemma4-26b, BOOLEAN, false",
        "gemma4:12b-mlx, gemma4-12b, BOOLEAN, false",
        "gemma4:e4b-mlx, gemma4-e4b, BOOLEAN, false",
        "gemma4:e2b-mlx, gemma4-e2b, BOOLEAN, false",
        "muse-glimmer:30b-nvfp4-dflash, muse-glimmer-30b, ALWAYS, low",
        "google/gemma-4-26b-a4b-qat, gemma4-26b, BOOLEAN, false",
        "google/gemma-4-e4b, gemma4-e4b, BOOLEAN, false",
        "lapa-v0.1.2-instruct, lapa, NONE,",
        "mamaylm-gemma-3-12b-it-v2.0, mamaylm-gemma-3-12b, NONE,",
        "mistralai/ministral-3-14b-reasoning, ministral-3-reasoning, ALWAYS,",
        "nvidia-nemotron-3.5-bolt-e4b, none, NONE,"
    })
    void resolve_ownerInstalledId_landsOnTheExpectedRow(
            final String id, final String row, final ReasoningControl.Kind kind, final @Nullable String offValue) {
        final ResolvedProfile resolved = resolve(id, ModelFacts.unknown());

        assertThat(rowName(id)).isEqualTo(row);
        assertThat(resolved.profile().reasoning().kind()).isEqualTo(kind);
        assertThat(resolved.profile().reasoning().offValue()).isEqualTo(offValue);
    }

    @Test
    void ownerIds_expectations_coverTheSixteenInstalledIds() throws IOException {
        final List<String> lines;
        try (InputStream in = BuiltInRowsTest.class.getResourceAsStream(OWNER_IDS)) {
            assertThat(in).isNotNull();
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .skip(1)
                    .toList();
        }

        final long withoutRow = lines.stream()
                .map(line -> rowName(inputOf(line)))
                .filter(NO_ROW::equals)
                .count();

        assertThat(lines).hasSize(16);
        assertThat(withoutRow)
                .as("only Nemotron 3.5 Bolt keeps the default profile")
                .isEqualTo(1);
    }

    private static String inputOf(final String line) {
        final String afterId = line.substring(line.indexOf(',') + 1);
        return afterId.substring(0, afterId.indexOf(','));
    }

    private List<String> rowsMatching(final String input) {
        final ModelIdentity identity = parser.parse(input).identity();
        return builtIn.rows().stream()
                .filter(r -> r.pattern().matches(identity))
                .map(r -> r.row().name())
                .toList();
    }

    private String rowName(final String id) {
        return builtIn.pick(parser.parse(id).identity(), ModelFacts.unknown())
                .map(p -> p.row().name())
                .orElse(NO_ROW);
    }

    private ResolvedProfile resolve(final String id, final ModelFacts facts) {
        return profiles.resolve(ServerEngine.OLLAMA, id, facts, ModelSettings.defaults("ollama", id));
    }

    private static ModelFacts factsOf(final String architecture) {
        return new ModelFacts(
                architecture,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                false);
    }
}
