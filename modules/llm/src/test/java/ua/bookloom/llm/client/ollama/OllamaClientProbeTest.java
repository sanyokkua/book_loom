package ua.bookloom.llm.client.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Exercises the Ollama native reachability probe over WireMock. */
class OllamaClientProbeTest {

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void probe_versionSuccess_reportsReachable() {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(okJson("{\"version\":\"0.34.2\"}")));

        final Result<Boolean> result = client().probe().result();

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).isTrue();
    }

    @Test
    void probe_serverError_returnsUpstream() {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(503)));

        assertThat(Objects.requireNonNull(client().probe().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.upstream);
    }

    @Test
    void probe_notFoundStillReportsReachable() {
        server.stubFor(get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(404)));

        assertThat(client().probe().result().data()).isTrue();
    }

    private OllamaClient client() {
        final ProviderConfig config = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create(server.baseUrl()),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
        return new OllamaClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }
}
