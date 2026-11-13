package ua.bookloom.llm.engine;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** The session's memory of what llama-server's template reads: one GET, kept until a Test forgets it. */
class EngineFactsTest {

    private static final String PROPS = """
            {"build_info":"b11669-620d2225c","role":"model","chat_template":"{% if enable_thinking %}{% endif %}"}
            """;

    private static final String LOADING = "{\"error\":{\"code\":503,\"message\":\"Loading model\"}}";
    private static final int SERVICE_UNAVAILABLE = 503;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private EngineFacts facts;
    private ProviderConfig config;

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(get(urlEqualTo("/props")).willReturn(okJson(PROPS)));
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        facts = new EngineFacts(exchange, new LlmModule().objectMapper());
        config = new ProviderConfig(
                        "llama",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2))
                .withEngine(ServerEngine.LLAMA_CPP);
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void readsEnableThinking_askedTwice_readsPropsOnce() {
        facts.readsEnableThinking(config, "qwen3.5-4b");

        assertThat(facts.readsEnableThinking(config, "qwen3.5-4b")).isTrue();
        server.verify(1, getRequestedFor(urlEqualTo("/props")));
    }

    @Test
    void readsEnableThinking_afterForget_readsPropsAgain() {
        facts.readsEnableThinking(config, "qwen3.5-4b");
        facts.forget("llama");

        facts.readsEnableThinking(config, "qwen3.5-4b");

        server.verify(2, getRequestedFor(urlEqualTo("/props")));
    }

    // llama-server answers 503 "Loading model" on every route while it loads: that is no answer about the template.
    @Test
    void readsEnableThinking_loadingThenReady_doesNotKeepTheLoadingReply() {
        server.stubFor(get(urlEqualTo("/props"))
                .inScenario("loading")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(jsonResponse(LOADING, SERVICE_UNAVAILABLE))
                .willSetStateTo("ready"));
        server.stubFor(get(urlEqualTo("/props"))
                .inScenario("loading")
                .whenScenarioStateIs("ready")
                .willReturn(okJson(PROPS)));

        final boolean whileLoading = facts.readsEnableThinking(config, "qwen3.5-4b");

        assertThat(whileLoading).isFalse();
        assertThat(facts.readsEnableThinking(config, "qwen3.5-4b")).isTrue();
        server.verify(2, getRequestedFor(urlEqualTo("/props")));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500, 503})
    void isRouter_propsNotOk_isAskedAgainNextTime(final int status) {
        server.stubFor(get(urlEqualTo("/props")).willReturn(jsonResponse("{\"role\":\"router\"}", status)));

        facts.isRouter(config);
        facts.isRouter(config);

        server.verify(2, getRequestedFor(urlEqualTo("/props")));
    }
}
