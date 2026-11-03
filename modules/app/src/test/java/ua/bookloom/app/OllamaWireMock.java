package ua.bookloom.app;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/**
 * A fake Ollama on a free local port, serving two invented model rows with the facts a real server reports for them.
 * The wire is faked, never our code (testing.md).
 */
final class OllamaWireMock implements AutoCloseable {

    static final String GLM = "hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL";
    static final String LLAMA = "llama3.1:8b";
    static final String PROVIDER_ID = "fake-ollama";

    private static final String SHOW = "/api/show";
    private static final String CHAT_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    OllamaWireMock() {
        server.start();
        server.stubFor(get(urlEqualTo("/api/tags"))
                .willReturn(okJson("{\"models\":[{\"name\":\"" + GLM + "\"},{\"name\":\"" + LLAMA + "\"}]}")));
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(okJson("{\"version\":\"0.12.0\"}")));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[]}")));
        server.stubFor(post(urlEqualTo("/api/chat")).willReturn(okJson(CHAT_REPLY)));
        server.stubFor(post(urlEqualTo(SHOW))
                .withRequestBody(matchingJsonPath("$[?(@.model == '" + GLM + "')]"))
                .willReturn(okJson(show("glm4moelite", "Q4_K_M", "29.9B"))));
        server.stubFor(post(urlEqualTo(SHOW))
                .withRequestBody(matchingJsonPath("$[?(@.model == '" + LLAMA + "')]"))
                .willReturn(okJson(show("llama", "Q4_K_M", "8.0B"))));
        server.stubFor(
                post(urlEqualTo(SHOW)).atPriority(10).willReturn(aResponse().withStatus(404)));
    }

    ProviderConfig provider() {
        return new ProviderConfig(
                PROVIDER_ID,
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                Duration.ofSeconds(20));
    }

    int showRequests() {
        return server.findAll(postRequestedFor(urlEqualTo(SHOW))).size();
    }

    int chatRequests() {
        return server.findAll(postRequestedFor(urlEqualTo("/api/chat"))).size();
    }

    int requests() {
        return server.getAllServeEvents().size();
    }

    private static String show(final String family, final String quant, final String size) {
        return "{\"capabilities\":[\"completion\"],\"details\":{\"family\":\"" + family
                + "\",\"format\":\"gguf\",\"quantization_level\":\"" + quant + "\",\"parameter_size\":\"" + size
                + "\"},\"model_info\":{\"general.architecture\":\"" + family + "\",\"" + family
                + ".context_length\":131072}}";
    }

    @Override
    public void close() {
        server.stop();
    }
}
