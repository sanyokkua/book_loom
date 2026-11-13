package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.LanguageDeclaration;
import ua.bookloom.api.llm.ListKind;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.QuantAdvice;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.knowledge.LanguageAdvisor;
import ua.bookloom.llm.knowledge.ModelKnowledgeFiles;
import ua.bookloom.llm.knowledge.QuantAdvisor;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.profile.ProfileRows;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** Context-length detection on both dialects at the HTTP seam, and its quiet degradation. */
class ModelCapabilityServiceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private FakeProviderConfigs configs;
    private ModelCapabilityService capabilities;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new FakeProviderConfigs();
        capabilities = new ModelCapabilityService(
                configs,
                new ProviderClientFactory(
                        new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime),
                new ModelProfiles(ProfileRows.NONE),
                new ModelSettingsCache(new FakeModelSettingsRepository()),
                new QuantAdvisor(new ModelKnowledgeFiles()),
                new LanguageAdvisor(new ModelKnowledgeFiles()));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void contextLength_ollamaShowWithArchitectureKey_returnsThatLength() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show"))
                .withRequestBody(equalToJson("{\"model\":\"gemma3:12b\"}"))
                .willReturn(okJson(
                        "{\"model_info\":{\"general.architecture\":\"gemma3\",\"gemma3.context_length\":131072}}")));

        final Result<ContextLength> result = capabilities.contextLength("ollama", "gemma3:12b");

        assertThat(result.data()).isEqualTo(new ContextLength(131072));
        server.verify(1, postRequestedFor(urlEqualTo("/api/show")));
    }

    @Test
    void contextLength_lmStudioLoadedModel_prefersLoadedOverMaximum() {
        register("lmstudio", ProviderKind.OPENAI_COMPATIBLE, server.baseUrl() + "/v1");
        server.stubFor(get(urlEqualTo("/api/v0/models/google/gemma-3"))
                .willReturn(okJson("{\"id\":\"google/gemma-3\",\"max_context_length\":131072,"
                        + "\"loaded_context_length\":4096}")));

        final Result<ContextLength> result = capabilities.contextLength("lmstudio", "google/gemma-3");

        assertThat(result.data()).isEqualTo(new ContextLength(4096));
    }

    @Test
    void contextLength_lmStudioUnloadedModel_returnsMaximum() {
        register("lmstudio", ProviderKind.OPENAI_COMPATIBLE, server.baseUrl() + "/v1");
        server.stubFor(get(urlEqualTo("/api/v0/models/qwen"))
                .willReturn(okJson("{\"id\":\"qwen\",\"max_context_length\":32768}")));

        assertThat(capabilities.contextLength("lmstudio", "qwen").data()).isEqualTo(new ContextLength(32768));
    }

    @Test
    void contextLength_plainOpenAiServerAnswers404_degradesToUnknownWithoutError() {
        register("openai", ProviderKind.OPENAI_COMPATIBLE, server.baseUrl() + "/v1");
        server.stubFor(
                get(urlEqualTo("/api/v0/models/gpt")).willReturn(aResponse().withStatus(404)));

        final Result<ContextLength> result = capabilities.contextLength("openai", "gpt");

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).isEqualTo(ContextLength.unknown());
    }

    @Test
    void contextLength_ollamaShowFailsWithServerError_degradesToUnknown() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(aResponse().withStatus(500)));

        assertThat(capabilities.contextLength("ollama", "m").data()).isEqualTo(ContextLength.unknown());
    }

    @Test
    void contextLength_ollamaReplyWithoutContextKey_degradesToUnknown() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson("{\"model_info\":{\"a.b\":1}}")));

        assertThat(capabilities.contextLength("ollama", "m").data()).isEqualTo(ContextLength.unknown());
    }

    @Test
    void contextLength_unreachableProvider_degradesToUnknown() {
        register("ollama", ProviderKind.OLLAMA, ClosedPorts.endpoint("").toString());

        assertThat(capabilities.contextLength("ollama", "m").data()).isEqualTo(ContextLength.unknown());
    }

    @Test
    void contextLength_unregisteredProvider_returnsValidationErrorWithoutRequest() {
        final Result<ContextLength> result = capabilities.contextLength("nobody", "m");

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private static final String SHOW_131072 =
            "{\"model_info\":{\"general.architecture\":\"gemma3\",\"gemma3.context_length\":131072}}";

    @Test
    void report_ollamaShowAndPs_returns131072And16384() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps"))
                .willReturn(okJson("{\"models\":[{\"name\":\"gemma3:12b\",\"context_length\":16384}]}")));

        final Result<ModelReport> result = capabilities.report(new ModelSelection("ollama", "gemma3:12b"));

        assertThat(result.data())
                .extracting(ModelReport::contextLength, ModelReport::loadedContext)
                .containsExactly(131072, 16384);
    }

    @Test
    void report_ollamaModelNotInPs_loadedAbsent() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps"))
                .willReturn(okJson("{\"models\":[{\"name\":\"other\",\"context_length\":4096}]}")));

        assertThat(capabilities
                        .report(new ModelSelection("ollama", "gemma3:12b"))
                        .data())
                .extracting(ModelReport::contextLength, ModelReport::loadedContext)
                .containsExactly(131072, null);
    }

    @Test
    void report_ollamaPsWithoutContextLength_loadedAbsent() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[{\"name\":\"gemma3:12b\"}]}")));

        assertThat(capabilities
                        .report(new ModelSelection("ollama", "gemma3:12b"))
                        .data())
                .extracting(ModelReport::contextLength, ModelReport::loadedContext)
                .containsExactly(131072, null);
    }

    @Test
    void report_ollamaPsServerError_keepsContextLength() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(aResponse().withStatus(500)));

        assertThat(capabilities
                        .report(new ModelSelection("ollama", "gemma3:12b"))
                        .data())
                .extracting(ModelReport::contextLength, ModelReport::loadedContext)
                .containsExactly(131072, null);
    }

    @Test
    void report_openAiCompatible_sendsNoPsRequestAndLoadedAbsent() {
        register("lmstudio", ProviderKind.OPENAI_COMPATIBLE, server.baseUrl() + "/v1");
        server.stubFor(get(urlEqualTo("/api/v0/models/qwen"))
                .willReturn(okJson("{\"id\":\"qwen\",\"max_context_length\":32768}")));

        final Result<ModelReport> result = capabilities.report(new ModelSelection("lmstudio", "qwen"));

        assertThat(result.data())
                .extracting(ModelReport::contextLength, ModelReport::loadedContext)
                .containsExactly(32768, null);
        server.verify(0, getRequestedFor(urlEqualTo("/api/ps")));
        server.verify(0, getRequestedFor(urlEqualTo("/v1/api/ps")));
    }

    @Test
    void report_genericServer_sendsNoApiPsAndNoLoadedFigure() {
        assertThat(configs.save(new ProviderConfig(
                                        "generic",
                                        ProviderKind.OPENAI_COMPATIBLE,
                                        URI.create(server.baseUrl() + "/v1"),
                                        TIMEOUT,
                                        TIMEOUT)
                                .withEngine(ServerEngine.GENERIC))
                        .isOk())
                .isTrue();
        server.stubFor(get(urlEqualTo("/api/ps"))
                .willReturn(okJson("{\"models\":[{\"name\":\"qwen\",\"context_length\":4096}]}")));
        final Result<ModelReport> result = capabilities.report(new ModelSelection("generic", "qwen"));
        assertThat(result.data()).extracting(ModelReport::loadedContext).isNull();
        server.verify(0, getRequestedFor(urlEqualTo("/api/ps")));
        server.verify(0, getRequestedFor(urlEqualTo("/v1/api/ps")));
    }

    @ParameterizedTest
    @CsvSource({
        "qwen2.5:1.5b, Q4_K_M, COMPLETE, ACCEPTABLE",
        "hf.co/bartowski/utter-project_EuroLLM-22B-Instruct-2512-GGUF:IQ4_NL, IQ4_NL, COMPLETE, GOOD",
        "nvidia-nemotron-3.5-bolt-e4b, Q4_K_M, NONE, ACCEPTABLE"
    })
    void report_ownerModel_carriesLanguageAndQuantAdvice(
            final String model, final String quant, final ListKind kind, final QuantTier tier) {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show"))
                .willReturn(okJson("{\"details\":{\"quantization_level\":\"" + quant + "\"},\"model_info\":{}}")));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[]}")));

        final ModelReport report =
                capabilities.report(new ModelSelection("ollama", model)).data();

        assertThat(report).isNotNull();
        assertThat(report.languages())
                .isNotNull()
                .extracting(LanguageDeclaration::listKind)
                .isEqualTo(kind);
        assertThat(report.quantAdvice())
                .isNotNull()
                .extracting(QuantAdvice::tier)
                .isEqualTo(tier);
    }

    @Test
    void report_qwen25Small_languagesDoNotListUkrainian() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[]}")));

        final ModelReport report = capabilities
                .report(new ModelSelection("ollama", "qwen2.5:1.5b"))
                .data();

        assertThat(report).isNotNull();
        assertThat(report.languages()).isNotNull();
        assertThat(report.languages().codes()).contains("ru").doesNotContain("uk");
    }

    @Test
    void report_unregistered_validationWithoutRequest() {
        final Result<ModelReport> result = capabilities.report(new ModelSelection("nobody", "m"));

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private void register(final String id, final ProviderKind kind, final String baseUrl) {
        assertThat(configs.save(new ProviderConfig(id, kind, URI.create(baseUrl), TIMEOUT, TIMEOUT))
                        .isOk())
                .isTrue();
    }

    private void registerLmStudio(final String id) {
        assertThat(configs.save(new ProviderConfig(
                                        id,
                                        ProviderKind.OPENAI_COMPATIBLE,
                                        URI.create(server.baseUrl() + "/v1"),
                                        TIMEOUT,
                                        TIMEOUT)
                                .withEngine(ServerEngine.LM_STUDIO))
                        .isOk())
                .isTrue();
    }

    private void stubLmStudioList(final String state) {
        server.stubFor(get(urlEqualTo("/api/v0/models"))
                .willReturn(okJson("{\"object\":\"list\",\"data\":[{\"id\":\"google/gemma-4-e4b\",\"type\":\"llm\","
                        + "\"state\":\"" + state + "\"}]}")));
    }

    // LM Studio loads a model that is not in memory with its own defaults, so Start says the window may differ.
    @Test
    void loadsWithOwnSettings_lmStudioModelNotLoaded_isTrue() {
        registerLmStudio("lmstudio");
        stubLmStudioList("not-loaded");

        assertThat(capabilities.loadsWithOwnSettings(new ModelSelection("lmstudio", "google/gemma-4-e4b")))
                .isTrue();
    }

    @Test
    void loadsWithOwnSettings_lmStudioModelLoaded_isFalse() {
        registerLmStudio("lmstudio");
        stubLmStudioList("loaded");

        assertThat(capabilities.loadsWithOwnSettings(new ModelSelection("lmstudio", "google/gemma-4-e4b")))
                .isFalse();
    }

    // IF another engine were asked, THEN every Start would send a GET whose answer says nothing about loading.
    @Test
    void loadsWithOwnSettings_ollama_sendsNothingAndIsFalse() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());

        assertThat(capabilities.loadsWithOwnSettings(new ModelSelection("ollama", "gemma4:e4b")))
                .isFalse();
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL|UD-Q4_K_XL|GOOD",
                "gemma4:e4b-mlx|''|",
                "llama3.1:8b|''|"
            })
    void quantAdviceByName_name_givesTheTokenAndTheTierTheNameAlreadyJustifies(
            final String name, final String token, final QuantTier tier) {
        final QuantAdvice advice = capabilities.quantAdviceByName(name);

        assertThat(advice).isNotNull();
        assertThat(advice.token()).isEqualTo(token);
        assertThat(advice.tier()).isEqualTo(tier);
        server.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void readPasted_hubBlobLinkOnOllama_givesLabelQuantAndPullCommand() {
        final var pasted = capabilities.readPasted(
                "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/blob/main/Qwen3.5-4B-Q4_K_M.gguf", ProviderKind.OLLAMA);

        assertThat(pasted).isPresent().hasValueSatisfying(p -> {
            assertThat(p.input().repo()).isEqualTo("unsloth/Qwen3.5-4B-GGUF");
            assertThat(p.modelLabel()).isEqualTo("Qwen 3.5");
            assertThat(p.quantToken()).isEqualTo("Q4_K_M");
            assertThat(p.pullCommand()).isEqualTo("ollama pull hf.co/unsloth/Qwen3.5-4B-GGUF:Q4_K_M");
        });
        server.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void readPasted_hubBlobLinkOnOpenAiCompatible_givesNoPullCommand() {
        final var pasted = capabilities.readPasted(
                "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/blob/main/Qwen3.5-4B-Q4_K_M.gguf",
                ProviderKind.OPENAI_COMPATIBLE);

        assertThat(pasted)
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.pullCommand()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemma4:e4b", "llama3.1:8b", "   "})
    void readPasted_plainId_isEmpty(final String text) {
        assertThat(capabilities.readPasted(text, ProviderKind.OLLAMA)).isEmpty();
    }
}
