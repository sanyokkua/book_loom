package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ContextLength;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
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
                        new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime));
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

        final Result<ModelReport> result = capabilities.report("ollama", "gemma3:12b");

        assertThat(result.data()).isEqualTo(new ModelReport(131072, 16384));
    }

    @Test
    void report_ollamaModelNotInPs_loadedAbsent() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps"))
                .willReturn(okJson("{\"models\":[{\"name\":\"other\",\"context_length\":4096}]}")));

        assertThat(capabilities.report("ollama", "gemma3:12b").data()).isEqualTo(new ModelReport(131072, null));
    }

    @Test
    void report_ollamaPsWithoutContextLength_loadedAbsent() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[{\"name\":\"gemma3:12b\"}]}")));

        assertThat(capabilities.report("ollama", "gemma3:12b").data()).isEqualTo(new ModelReport(131072, null));
    }

    @Test
    void report_ollamaPsServerError_keepsContextLength() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(aResponse().withStatus(500)));

        assertThat(capabilities.report("ollama", "gemma3:12b").data()).isEqualTo(new ModelReport(131072, null));
    }

    @Test
    void report_openAiCompatible_sendsNoPsRequestAndLoadedAbsent() {
        register("lmstudio", ProviderKind.OPENAI_COMPATIBLE, server.baseUrl() + "/v1");
        server.stubFor(get(urlEqualTo("/api/v0/models/qwen"))
                .willReturn(okJson("{\"id\":\"qwen\",\"max_context_length\":32768}")));

        final Result<ModelReport> result = capabilities.report("lmstudio", "qwen");

        assertThat(result.data()).isEqualTo(new ModelReport(32768, null));
        server.verify(0, getRequestedFor(urlEqualTo("/api/ps")));
        server.verify(0, getRequestedFor(urlEqualTo("/v1/api/ps")));
    }

    @Test
    void report_unregistered_validationWithoutRequest() {
        final Result<ModelReport> result = capabilities.report("nobody", "m");

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    private void register(final String id, final ProviderKind kind, final String baseUrl) {
        assertThat(configs.save(new ProviderConfig(id, kind, URI.create(baseUrl), TIMEOUT, TIMEOUT))
                        .isOk())
                .isTrue();
    }
}
