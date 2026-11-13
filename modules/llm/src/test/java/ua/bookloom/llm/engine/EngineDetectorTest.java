package ua.bookloom.llm.engine;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.ClosedPorts;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Proves on the wire that the engine is told by the shape of the reply bodies, never by a port or a status (D17). */
class EngineDetectorTest {

    private static final String VLLM_MODELS =
            "{\"object\":\"list\",\"data\":[{\"id\":\"mara-7b\",\"object\":\"model\",\"owned_by\":\"vllm\"}]}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private EngineDetector detector;

    @BeforeEach
    void startServer() {
        server.start();
        detector = new EngineDetector(
                new HttpExchange(new HttpClients(), System::nanoTime, Map.<String, String>of()::get),
                new LlmModule().objectMapper());
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void detect_realOllamaBodies_isOllamaWithVersion() {
        RecordedServers.OLLAMA.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OLLAMA, ""));

        assertThat(detection.engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(detection.version()).isEqualTo("0.40.11");
        assertThat(detection.probes()).isEqualTo(3);
        assertThat(detection.source()).isEqualTo(Source.DETECTED);
        assertThat(detection.offerNativeConnection()).isFalse();
    }

    @Test
    void detect_ollamaOnNonDefaultPort_isStillOllama() {
        RecordedServers.OLLAMA.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(server.port()).isNotEqualTo(11434);
        assertThat(detection.engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(detection.offerNativeConnection()).isTrue();
    }

    @Test
    void detect_ollamaBehindOpenAiKindWithOfferDeclined_offersNothing() {
        RecordedServers.OLLAMA.stub(server);
        final ProviderConfig declined = withDeclinedOffer(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        final EngineDetection detection = detected(declined);

        assertThat(detection.engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(detection.offerNativeConnection()).isFalse();
    }

    @Test
    void detect_koboldCppFakeTags_isNotOllama() {
        RecordedServers.KOBOLDCPP.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.GENERIC);
    }

    @Test
    void detect_localAiRowsWithoutOwnedBy_isNotOllamaAndUsesSevenGets() {
        RecordedServers.LOCALAI.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.GENERIC);
        assertThat(detection.probes()).isEqualTo(EngineDetector.MAX_PROBES);
        assertThat(server.getAllServeEvents()).hasSize(EngineDetector.MAX_PROBES);
        server.verify(1, getRequestedFor(urlEqualTo("/v1/models")));
    }

    @Test
    void detect_ollamaWhoseV1ModelsIs404_isStillOllama() {
        RecordedServers.OLLAMA_WITHOUT_V1.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OLLAMA, ""));

        assertThat(detection.engine()).isEqualTo(ServerEngine.OLLAMA);
        assertThat(detection.version()).isEqualTo("0.40.11");
    }

    @Test
    void detect_lmStudioAnswering200ToEveryPath_isLmStudio() {
        RecordedServers.LM_STUDIO.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.LM_STUDIO);
        assertThat(detection.version()).isNull();
        assertThat(detection.probes()).isEqualTo(5);
    }

    @Test
    void detect_lmStudioOnNonDefaultPort_isLmStudio() {
        RecordedServers.LM_STUDIO.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OLLAMA, ""));

        assertThat(server.port()).isNotEqualTo(1234);
        assertThat(detection.engine()).isEqualTo(ServerEngine.LM_STUDIO);
    }

    @Test
    void detect_llamaServerHeaderAndProps_isLlamaCppWithBuildVersion() {
        RecordedServers.LLAMA_SERVER.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(detection.version()).isEqualTo("b11669");
        assertThat(detection.router()).isFalse();
        assertThat(detection.probes()).isEqualTo(3);
    }

    @Test
    void detect_llamaServerRouterProps_isLlamaCppWithRouterFlag() {
        RecordedServers.LLAMA_ROUTER.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(detection.router()).isTrue();
    }

    @Test
    void detect_llamaServerLoadingModel_isLlamaCpp() {
        server.stubFor(
                get(urlEqualTo("/health"))
                        .willReturn(
                                aResponse()
                                        .withStatus(503)
                                        .withBody(
                                                "{\"error\":{\"code\":503,\"message\":\"Loading model\",\"type\":\"unavailable_error\"}}")));

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.LLAMA_CPP);
    }

    @Test
    void detect_healthOkWithoutBuildInfo_isNotLlamaCpp() {
        server.stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse().withStatus(200).withBody("{\"status\":\"ok\"}")));

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.GENERIC);
    }

    @Test
    void detect_unslothHealth_isUnsloth() {
        RecordedServers.UNSLOTH.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.UNSLOTH);
    }

    @Test
    void detect_hostOpenRouterAi_isOpenRouterWithNoRequestSent() {
        final ProviderConfig openRouter = new ProviderConfig(
                "router",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://openrouter.ai/api/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));

        final EngineDetection detection = detected(openRouter);

        assertThat(detection.engine()).isEqualTo(ServerEngine.OPENROUTER);
        assertThat(detection.probes()).isZero();
    }

    @Test
    void detect_hostAnthropic_isNotSupportedYetWithNoRequestSent() {
        final ProviderConfig anthropic = new ProviderConfig(
                "claude",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://api.anthropic.com/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));

        final EngineDetection detection = detected(anthropic);

        assertThat(detection.suitability()).isEqualTo(Suitability.NOT_SUPPORTED_YET);
        assertThat(detection.probes()).isZero();
    }

    @Test
    void detect_gpt4allOwnedByHumanity_isNotSuitable() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(RecordedServers.json(RecordedServers.GPT4ALL_MODELS)));

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.suitability()).isEqualTo(Suitability.NOT_SUITABLE_STRUCTURED);
    }

    @Test
    void detect_anythingLlmPath_isNotSuitableWithNoRequestSent() {
        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/api/v1/openai"));

        assertThat(detection.suitability()).isEqualTo(Suitability.NOT_SUITABLE_DOCUMENTS);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void detect_vllmOwnedRows_isGeneric() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(RecordedServers.json(VLLM_MODELS)));

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.GENERIC);
        assertThat(detection.suitability()).isEqualTo(Suitability.SUITABLE);
    }

    @Test
    void detect_overrideChoiceSet_wins() {
        RecordedServers.OLLAMA.stub(server);
        final ProviderConfig chosen = provider(ProviderKind.OPENAI_COMPATIBLE, "/v1")
                .withEngine(ServerEngine.GENERIC, ServerEngine.LLAMA_CPP, null);

        final EngineDetection detection = detected(chosen);

        assertThat(detection.engine()).isEqualTo(ServerEngine.LLAMA_CPP);
        assertThat(detection.source()).isEqualTo(Source.OVERRIDE);
        assertThat(detection.probes()).isZero();
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void detect_nothingListening_returnsUnreachableAfterOneGet() {
        final ProviderConfig closed = new ProviderConfig(
                "closed",
                ProviderKind.OPENAI_COMPATIBLE,
                ClosedPorts.endpoint("/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));

        final Result<EngineDetection> result = detector.detect(closed);

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void detect_bearerProvider_sendsTheKeyOnEveryProbe() {
        detector = new EngineDetector(
                new HttpExchange(new HttpClients(), System::nanoTime, Map.of("PROBE_KEY", "sk-probe-0000")::get),
                new LlmModule().objectMapper());
        RecordedServers.UNSLOTH.stub(server);
        final ProviderConfig keyed = provider(ProviderKind.OPENAI_COMPATIBLE, "/v1")
                .withAuth(ProviderAuth.BEARER)
                .withCredentialRef("PROBE_KEY");

        final EngineDetection detection = detected(keyed);

        assertThat(detection.engine()).isEqualTo(ServerEngine.UNSLOTH);
        assertThat(server.getAllServeEvents())
                .allSatisfy(event -> assertThat(event.getRequest().getHeader("Authorization"))
                        .isEqualTo("Bearer sk-probe-0000"));
    }

    @Test
    void detect_unkeyedProvider_sendsNoAuthorizationHeader() {
        RecordedServers.UNSLOTH.stub(server);

        detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(server.getAllServeEvents())
                .allSatisfy(event -> assertThat(event.getRequest().containsHeader("Authorization"))
                        .isFalse());
    }

    @ParameterizedTest
    @EnumSource(RecordedServers.class)
    void detect_anyServer_neverSendsMoreThanSevenGets(final RecordedServers kind) {
        kind.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/v1"));

        assertThat(detection.probes()).isLessThanOrEqualTo(EngineDetector.MAX_PROBES);
        assertThat(server.getAllServeEvents()).hasSize(detection.probes());
        assertThat(server.getAllServeEvents())
                .allSatisfy(event ->
                        assertThat(event.getRequest().getMethod().getName()).isEqualTo("GET"));
        assertThat(server.getAllServeEvents())
                .allSatisfy(event -> assertThat(event.getRequest().getBody()).isEmpty());
    }

    // A base below another prefix (Docker's /engines/llama.cpp/v1) whose root already answered /v1/models in the Ollama
    // step reuses that reply for the model list, so even this path stays within seven GETs.
    @Test
    void detect_baseBelowAnotherPrefixAfterRootModels_reusesTheRootReplyWithinSevenGets() {
        RecordedServers.LOCALAI.stub(server);

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/engines/llama.cpp/v1"));

        assertThat(detection.probes()).isEqualTo(EngineDetector.MAX_PROBES);
        assertThat(server.getAllServeEvents()).hasSize(EngineDetector.MAX_PROBES);
        server.verify(0, getRequestedFor(urlEqualTo("/engines/llama.cpp/v1/models")));
        server.verify(1, getRequestedFor(urlEqualTo("/v1/models")));
    }

    // When the Ollama step never asked the root's /v1/models, the base's own list is read.
    @Test
    void detect_baseBelowAnotherPrefixWithoutRootModels_readsTheBaseList() {
        server.stubFor(get(urlEqualTo("/engines/llama.cpp/v1/models")).willReturn(RecordedServers.json(VLLM_MODELS)));

        final EngineDetection detection = detected(provider(ProviderKind.OPENAI_COMPATIBLE, "/engines/llama.cpp/v1"));

        assertThat(detection.engine()).isEqualTo(ServerEngine.GENERIC);
        assertThat(detection.probes()).isLessThanOrEqualTo(EngineDetector.MAX_PROBES);
        server.verify(1, getRequestedFor(urlEqualTo("/engines/llama.cpp/v1/models")));
    }

    private EngineDetection detected(final ProviderConfig config) {
        final Result<EngineDetection> result = detector.detect(config);
        assertThat(result.isOk()).as("detection: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "detection");
    }

    private ProviderConfig provider(final ProviderKind kind, final String path) {
        return new ProviderConfig(
                "probe", kind, URI.create(server.baseUrl() + path), Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    private static ProviderConfig withDeclinedOffer(final ProviderConfig config) {
        return new ProviderConfig(
                config.id(),
                config.kind(),
                config.baseUrl(),
                config.connectTimeout(),
                config.requestTimeout(),
                null,
                ProviderAuth.NONE,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(),
                ServerEngine.OLLAMA,
                null,
                null,
                true);
    }
}
