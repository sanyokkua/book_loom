package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** Exercises the real JDK HTTP exchange at a WireMock server boundary. */
class HttpExchangeTest {

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
    void post_jsonRequestUsesJsonHeadersAndOmitsAuthorization() {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post("/chat")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withBody("{}")));

        final Result<HttpReply> result = exchange()
                .post(config(URI.create(server.baseUrl()), Duration.ofSeconds(2)), "/chat", "{\"prompt\":\"hello\"}");

        assertThat(result.isOk()).isTrue();
        server.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/chat"))
                .withHeader("Content-Type", com.github.tomakehurst.wiremock.client.WireMock.equalTo("application/json"))
                .withHeader("Accept", com.github.tomakehurst.wiremock.client.WireMock.equalTo("application/json"))
                .withoutHeader("Authorization"));
    }

    @Test
    void get_delayedResponseMapsRequestTimeoutWithSafeTimeoutDetail() {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/slow")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(200)
                        .withFixedDelay(3000)
                        .withBody("{}")));

        final Result<HttpReply> result =
                exchange().get(config(URI.create(server.baseUrl()), Duration.ofSeconds(1)), "/slow");

        assertThat(result.isErr()).isTrue();
        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.timeout);
        assertThat(error.details()).contains("timeoutMs=1000");
    }

    @Test
    void get_closedLocalPortMapsToUnreachable() throws IOException {
        final URI endpoint = closedEndpoint();

        final Result<HttpReply> result = exchange().get(config(endpoint, Duration.ofSeconds(2)), "/chat");

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.unreachable);
    }

    @Test
    void get_nonSuccessResponseRetainsStatusHeadersAndBody() {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/limited")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "17")
                        .withBody("rate limit response")));

        final Result<HttpReply> result =
                exchange().get(config(URI.create(server.baseUrl()), Duration.ofSeconds(2)), "/limited");

        assertThat(result.isOk()).isTrue();
        final HttpReply reply = Objects.requireNonNull(result.data(), "data");
        assertThat(reply.status()).isEqualTo(429);
        assertThat(reply.headers()).containsEntry("retry-after", List.of("17"));
        assertThat(reply.body()).isEqualTo("rate limit response");
    }

    @Test
    void get_basePathPrefix_isPreservedWhenJoiningRoute() {
        server.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/v1/models")
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.okJson("{}")));

        final Result<HttpReply> result =
                exchange().get(config(URI.create(server.baseUrl() + "/v1"), Duration.ofSeconds(2)), "/models");

        assertThat(result.isOk()).isTrue();
        server.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/v1/models")));
    }

    @Test
    void httpReply_withMutableHeaders_copiesAndFreezesTheHeaderMap() {
        final Map<String, List<String>> input = new java.util.HashMap<>();
        input.put("X-Reply", new java.util.ArrayList<>(List.of("first")));
        final HttpReply reply = new HttpReply(200, input, "{}");
        input.get("X-Reply").add("later");
        input.put("X-Added", List.of("later"));

        assertThat(reply.headers()).containsEntry("X-Reply", List.of("first")).doesNotContainKey("X-Added");
        assertThatThrownBy(() -> Objects.requireNonNull(reply.headers().get("X-Reply"), "header")
                        .add("mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> reply.headers().put("X-Added", List.of("mutation")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private HttpExchange exchange() {
        return new HttpExchange(new HttpClients());
    }

    private static ProviderConfig config(URI baseUrl, Duration requestTimeout) {
        return new ProviderConfig(
                "test-provider", ProviderKind.OPENAI_COMPATIBLE, baseUrl, Duration.ofSeconds(2), requestTimeout);
    }

    private static URI closedEndpoint() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            final int port = socket.getLocalPort();
            return URI.create("http://127.0.0.1:" + port);
        }
    }
}
