package ua.bookloom.llm.capability;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.engine.EngineFacts;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;

/** What each local engine says about a model, at the HTTP seam; the wire shapes follow the research snapshot. */
class ModelFactsReaderTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String ROUTER_MODEL = "model-a.gguf";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ObjectMapper mapper = new LlmModule().objectMapper();
    private ModelFactsReader reader;

    @BeforeEach
    void start() {
        server.start();
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        reader = new ModelFactsReader(exchange, mapper, new EngineFacts(exchange, mapper));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void read_ollamaShowQwen35Nvfp4_hasRendererParserAndThinkingValues() {
        server.stubFor(post(urlEqualTo("/api/show"))
                .willReturn(okJson("{\"capabilities\":[\"completion\",\"tools\",\"thinking\"],"
                        + "\"thinking\":{\"values\":[\"low\",\"high\"],\"default\":\"high\"},"
                        + "\"renderer\":\"qwen3.5\",\"parser\":\"qwen3.5\","
                        + "\"details\":{\"family\":\"qwen3_5\",\"format\":\"safetensors\","
                        + "\"quantization_level\":\"nvfp4\",\"parameter_size\":\"9.7B\"},"
                        + "\"model_info\":{\"general.architecture\":\"qwen3_5\",\"qwen3_5.context_length\":262144}}")));
        server.stubFor(get(urlEqualTo("/api/ps"))
                .willReturn(okJson("{\"models\":[{\"name\":\"m:9b\",\"context_length\":16384,\"runner\":\"mlx\"}]}")));

        final ModelFacts facts = reader.read(ollama(), "m:9b");

        assertThat(facts.renderer()).isEqualTo("qwen3.5");
        assertThat(facts.parser()).isEqualTo("qwen3.5");
        assertThat(facts.thinkingOptions()).containsExactly("low", "high");
        assertThat(facts.thinkingDefault()).isEqualTo("high");
        assertThat(facts.capabilities()).contains("thinking");
        assertThat(facts.family()).isEqualTo("qwen3_5");
        assertThat(facts.quant()).isEqualTo("nvfp4");
        assertThat(facts.architecture()).isEqualTo("qwen3_5");
        assertThat(facts.contextLength()).isEqualTo(262144);
        assertThat(facts.loadedContext()).isEqualTo(16384);
        assertThat(facts.runner()).isEqualTo("mlx");
    }

    @Test
    void read_hfGgufWithoutRenderer_hasNoRendererAndNoThinkingCapability() {
        server.stubFor(post(urlEqualTo("/api/show"))
                .willReturn(okJson("{\"capabilities\":[\"completion\"],\"details\":{\"family\":\"qwen35\","
                        + "\"format\":\"gguf\",\"quantization_level\":\"Q4_K_M\"},"
                        + "\"model_info\":{\"general.architecture\":\"qwen35\",\"qwen35.context_length\":262144}}")));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[]}")));

        final ModelFacts facts = reader.read(ollama(), "hf.co/user/Model-GGUF:Q4_K_M");

        assertThat(facts.renderer()).isNull();
        assertThat(facts.capabilities()).doesNotContain("thinking");
        assertThat(facts.thinkingOptions()).isEmpty();
        assertThat(facts.loadedContext()).isNull();
    }

    @Test
    void read_lmStudioReasoningOnly_listsAllowedOptionsOn() {
        server.stubFor(get(urlEqualTo("/api/v0/models/org/reasoner"))
                .willReturn(okJson("{\"id\":\"org/reasoner\",\"type\":\"llm\",\"arch\":\"mistral3\","
                        + "\"compatibility_type\":\"gguf\",\"state\":\"not-loaded\",\"quantization\":\"Q4_K_M\","
                        + "\"max_context_length\":262144}")));
        server.stubFor(get(urlEqualTo("/api/v1/models"))
                .willReturn(okJson("{\"models\":[{\"key\":\"org/reasoner\",\"capabilities\":{\"reasoning\":"
                        + "{\"allowed_options\":[\"on\"],\"default\":\"on\"}}}]}")));

        final ModelFacts facts = reader.read(config(ServerEngine.LM_STUDIO), "org/reasoner");

        assertThat(facts.thinkingOptions()).containsExactly("on");
        assertThat(facts.thinkingDefault()).isEqualTo("on");
        assertThat(facts.architecture()).isEqualTo("mistral3");
        assertThat(facts.state()).isEqualTo("not-loaded");
        assertThat(facts.compatibility()).isEqualTo("gguf");
        assertThat(facts.contextLength()).isEqualTo(262144);
    }

    @Test
    void read_llamaServerRouterModel_usesPropsForThatIdNotTheRouterWindow() {
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson("{\"role\":\"router\",\"n_ctx\":0}")));
        server.stubFor(get(urlEqualTo("/props?model=" + ROUTER_MODEL))
                .willReturn(okJson("{\"role\":\"model\",\"n_ctx\":8192}")));

        final ModelFacts facts = reader.read(config(ServerEngine.LLAMA_CPP), ROUTER_MODEL);

        assertThat(facts.contextLength()).isEqualTo(8192);
        assertThat(facts.loadedContext()).isEqualTo(8192);
    }

    @Test
    void read_llamaServerRouterWithoutModelProps_fallsBackToTheListingRowsSlot() {
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson("{\"role\":\"router\",\"n_ctx\":0}")));
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"data\":[{\"id\":\"" + ROUTER_MODEL + "\",\"meta\":{\"n_ctx\":4096}}]}")));

        assertThat(reader.read(config(ServerEngine.LLAMA_CPP), ROUTER_MODEL).contextLength())
                .isEqualTo(4096);
    }

    @Test
    void read_unslothRow_givesQuantAndTheLoadedWindow() {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"data\":[{\"id\":\"m\",\"quant\":\"Q4_K_XL\",\"context_length\":8192,"
                        + "\"max_context_length\":131072,\"loaded\":true}]}")));

        final ModelFacts facts = reader.read(config(ServerEngine.UNSLOTH), "m");

        assertThat(facts.quant()).isEqualTo("Q4_K_XL");
        assertThat(facts.contextLength()).isEqualTo(8192);
    }

    @Test
    void read_genericListing_usesMaxModelLen() {
        server.stubFor(get(urlEqualTo("/v1/models"))
                .willReturn(okJson("{\"data\":[{\"id\":\"m\",\"max_model_len\":32768}]}")));

        assertThat(reader.read(config(ServerEngine.GENERIC), "m").contextLength())
                .isEqualTo(32768);
    }

    @Test
    void read_serverCannotSay_isUnknownNotAnError() {
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(aResponse().withStatus(500)));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("not json")));

        assertThat(reader.read(ollama(), "m")).isEqualTo(ModelFacts.unknown());
    }

    @Test
    void read_noFactsAreReadInTheBackground() {
        final ProviderClientFactory clients =
                new ProviderClientFactory(new HttpExchange(new HttpClients()), mapper, System::nanoTime);
        clients.create(ollama());
        clients.create(config(ServerEngine.LM_STUDIO));

        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void read_aRead_isKeptForTheSessionOnly() {
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        final EngineFacts session = new EngineFacts(exchange, mapper);
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson("{\"capabilities\":[\"completion\"]}")));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{}")));

        new ModelFactsReader(exchange, mapper, session).read(ollama(), "m");

        assertThat(session.lastRead("ollama", "m"))
                .hasValueSatisfying(kept -> assertThat(kept.capabilities()).isEqualTo(List.of("completion")));
        session.forget("ollama");
        assertThat(session.lastRead("ollama", "m")).isEmpty();
    }

    private ProviderConfig ollama() {
        return new ProviderConfig("ollama", ProviderKind.OLLAMA, URI.create(server.baseUrl()), TIMEOUT, TIMEOUT);
    }

    private ProviderConfig config(final ServerEngine engine) {
        return new ProviderConfig(
                        "openai",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        TIMEOUT,
                        TIMEOUT)
                .withEngine(engine);
    }
}
