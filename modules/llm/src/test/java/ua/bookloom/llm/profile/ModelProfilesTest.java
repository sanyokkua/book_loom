package ua.bookloom.llm.profile;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ContextPolicy;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.ModelCapabilityService;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/**
 * The fixed order of a model's handling: a row picked by the name, the server's facts over the row, the person's
 * overrides over everything, and the safe default when no row knows the model. The rows here are a small invented test
 * set; the built-in rows are not part of this resolver.
 */
class ModelProfilesTest {

    private static final int ROW_CAP = 32_768;
    private static final int ROW_BATCH = 4;

    // A Qwen 3.5 row that thinks by default and has an on/off switch, as the test's only row.
    private static final ProfileRow QWEN35 = new ProfileRow(
            "test qwen 3.5",
            new ReasoningControl(ReasoningControl.Kind.BOOLEAN, List.of("true", "false"), "false", "true"),
            Set.of(),
            ModelProfile.SystemRole.NATIVE,
            Set.of(),
            ROW_CAP,
            4_096,
            0,
            ROW_BATCH,
            ModelProfile.Tier.SMALL,
            "recommended");

    private static final ProfileRows TEST_ROWS =
            (identity, facts) -> "qwen".equals(identity.family()) && "3.5".equals(identity.version())
                    ? Optional.of(new ProfileRows.Picked(QWEN35, ModelReport.Match.FAMILY_VERSION))
                    : Optional.empty();

    private final ModelProfiles profiles = new ModelProfiles(TEST_ROWS);

    @Test
    void resolve_serverFactsDisagreeWithRow_serverFactsWin() {
        final String model = "hf.co/someuser/Qwen3.5-9B-GGUF:Q4_K_M";
        final ModelFacts facts = ollamaFacts(List.of("completion", "tools"), null, 40_960, 8_192);

        final ResolvedProfile resolved =
                profiles.resolve(ServerEngine.OLLAMA, model, facts, ModelSettings.defaults("ollama", model));

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.FAMILY_VERSION);
        assertThat(resolved.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.UNSWITCHABLE);
        assertThat(resolved.profile().context()).isEqualTo(new ContextPolicy(ROW_CAP, 4_096, 40_960, 8_192));
        assertThat(resolved.profile().batchSize()).isEqualTo(ROW_BATCH);
        assertThat(resolved.quant()).isEqualTo("q4_k_m");
    }

    @Test
    void report_beforeAModelExists_isServedBySelection() {
        final WireMockServer server =
                new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
        server.start();
        try {
            server.stubFor(post(urlEqualTo("/api/show"))
                    .willReturn(okJson("{\"capabilities\":[\"completion\",\"tools\"],\"details\":{\"family\":\"qwen2\","
                            + "\"format\":\"gguf\",\"quantization_level\":\"Q4_K_M\",\"parameter_size\":\"1.5B\"}}")));
            server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[]}")));

            final ModelReport report = capabilities(server)
                    .report(new ModelSelection("ollama", "qwen2.5:1.5b"))
                    .data();

            assertThat(report).isNotNull();
            assertThat(report.identity()).isNotNull();
            assertThat(report.identity().family()).isEqualTo("qwen");
            assertThat(report.identity().version()).isEqualTo("2.5");
            assertThat(report.identity().sizeB()).isEqualTo(1.5);
            assertThat(report.match()).isEqualTo(ModelReport.Match.DEFAULT);
            assertThat(report.verdict()).isEqualTo("unknown model");
            server.verify(0, postRequestedFor(urlEqualTo("/api/chat")));
            server.verify(1, postRequestedFor(anyUrl()));
        } finally {
            server.stop();
        }
    }

    @Test
    void resolve_personOverride_winsOverEverything() {
        final SamplingOverride custom = SamplingOverride.custom(0.4, 20, 0.9, 0.05);
        final ModelSettings settings =
                ModelSettings.defaults("ollama", "qwen3.5:9b").withOverrides(ThinkingOverride.ALWAYS, custom);
        final ModelFacts facts = ollamaFacts(List.of("completion", "thinking"), "qwen3.5", 262_144, null);

        final ResolvedProfile resolved = profiles.resolve(ServerEngine.OLLAMA, "qwen3.5:9b", facts, settings);

        assertThat(resolved.match()).isEqualTo(ModelReport.Match.OVERRIDE);
        assertThat(resolved.profile().thinking()).isEqualTo(ThinkingOverride.ALWAYS);
        assertThat(resolved.profile().sampling()).isEqualTo(custom);
        assertThat(resolved.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.BOOLEAN);
        assertThat(resolved.verdict()).isEqualTo("recommended");
    }

    @Test
    void resolve_noRowMatches_isTheSafeDefault() {
        final ResolvedProfile resolved = profiles.resolve(
                ServerEngine.GENERIC, "mara-7b", ModelFacts.unknown(), ModelSettings.defaults("office", "mara-7b"));

        assertThat(resolved.profile()).isEqualTo(ModelProfile.defaults());
        assertThat(resolved.profile().batchSize()).isEqualTo(2);
        assertThat(resolved.profile().quirks()).containsExactly(ModelProfile.Quirk.PROMPT_ONLY_START);
        assertThat(resolved.profile().reasoning().kind()).isEqualTo(ReasoningControl.Kind.NONE);
        assertThat(resolved.match()).isEqualTo(ModelReport.Match.DEFAULT);
        assertThat(resolved.verdict()).isEqualTo("unknown model");
        assertThat(resolved.report().warnings()).isEmpty();
    }

    // An unknown model still gets its own facts: a server that lists a thinking capability gives it the switch.
    @Test
    void resolve_noRowButServerListsThinking_takesTheServersSwitch() {
        final ModelFacts facts = ollamaFacts(List.of("completion", "thinking"), "mara", null, null);

        final ResolvedProfile resolved =
                profiles.resolve(ServerEngine.OLLAMA, "mara-7b", facts, ModelSettings.defaults("ollama", "mara-7b"));

        assertThat(resolved.profile().reasoning())
                .isEqualTo(new ReasoningControl(ReasoningControl.Kind.BOOLEAN, List.of(), "false", null));
        assertThat(resolved.match()).isEqualTo(ModelReport.Match.DEFAULT);
    }

    @Test
    void resolve_levelsOfferedByTheServer_offIsTheLowestLevel() {
        final ModelFacts facts = new ModelFacts(
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of("low", "medium", "high"),
                "medium",
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

        final ResolvedProfile resolved =
                profiles.resolve(ServerEngine.LM_STUDIO, "mara-7b", facts, ModelSettings.defaults("lms", "mara-7b"));

        assertThat(resolved.profile().reasoning())
                .isEqualTo(new ReasoningControl(
                        ReasoningControl.Kind.LEVELS, List.of("low", "medium", "high"), "low", "medium"));
    }

    // A generic server's switch is only known once a check proved it; until then no control is sent.
    @Test
    void resolve_genericEngineWithoutThinkingCheck_isUnknownUntilHonoured() {
        final String model = "qwen3.5-9b";
        final ModelSettings unchecked = ModelSettings.defaults("office", model);
        final ModelSettings checked = unchecked.withProbeVerdicts(new ProbeVerdicts(
                null,
                new ProbeVerdicts.ThinkingVerdict(
                        "reasoning_effort",
                        new ProbeVerdicts.Verdict(
                                ProbeVerdicts.Outcome.HONOURED, "test", Instant.parse("2026-10-30T10:00:00Z"))),
                null,
                null));

        assertThat(profiles.resolve(ServerEngine.GENERIC, model, ModelFacts.unknown(), unchecked)
                        .profile()
                        .reasoning()
                        .kind())
                .isEqualTo(ReasoningControl.Kind.UNKNOWN);
        assertThat(profiles.resolve(ServerEngine.GENERIC, model, ModelFacts.unknown(), checked)
                        .profile()
                        .reasoning()
                        .kind())
                .isEqualTo(ReasoningControl.Kind.BOOLEAN);
    }

    private static ModelFacts ollamaFacts(
            final List<String> capabilities,
            final @Nullable String renderer,
            final @Nullable Integer contextLength,
            final @Nullable Integer loaded) {
        return new ModelFacts(
                null,
                null,
                "gguf",
                null,
                null,
                capabilities,
                List.of(),
                null,
                renderer,
                null,
                null,
                null,
                null,
                null,
                contextLength,
                loaded,
                List.of(),
                null,
                false);
    }

    private static ModelCapabilityService capabilities(final WireMockServer server) {
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        configs.save(new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)));
        return new ModelCapabilityService(
                configs,
                new ProviderClientFactory(
                        new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime),
                new ModelProfiles(TEST_ROWS),
                new ModelSettingsCache(new FakeModelSettingsRepository()));
    }
}
