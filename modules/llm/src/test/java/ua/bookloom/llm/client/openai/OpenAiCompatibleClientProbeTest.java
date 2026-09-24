package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Verifies OpenAI-compatible reachability probing through the models endpoint. */
class OpenAiCompatibleClientProbeTest {

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
    void kind_reportsOpenAiCompatibleDialect() {
        assertThat(client().kind()).isEqualTo(ProviderKind.OPENAI_COMPATIBLE);
    }

    @Test
    void probe_notFoundStillReportsReachable() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(aResponse().withStatus(404)));

        assertThat(client().probe().result().data()).isTrue();
    }

    @Test
    void probe_authFailureReturnsAuth() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(aResponse().withStatus(401)));

        assertThat(Objects.requireNonNull(client().probe().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.auth);
    }

    @Test
    void probe_serverFailureReturnsUpstream() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(aResponse().withStatus(503)));

        assertThat(Objects.requireNonNull(client().probe().result().error(), "error")
                        .code())
                .isEqualTo(ErrorCode.upstream);
    }

    private OpenAiCompatibleClient client() {
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        return new OpenAiCompatibleClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }
}
