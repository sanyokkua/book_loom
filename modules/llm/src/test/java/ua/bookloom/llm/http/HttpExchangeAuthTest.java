package ua.bookloom.llm.http;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.ClosedPorts;

/** Sign-in headers on the wire: what is sent, what is refused before any request, and what never leaks. */
class HttpExchangeAuthTest {

    private static final String KEY_VARIABLE = "BOOKLOOM_TEST_KEY";
    private static final String KEY_VALUE = "sk-test-123";
    private static final String MODELS_PATH = "/v1/models";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(get(urlEqualTo(MODELS_PATH)).willReturn(okJson("{\"data\":[]}")));
        server.stubFor(post(urlEqualTo(MODELS_PATH)).willReturn(okJson("{}")));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF the provider signs in with a bearer token, THEN the request carries Authorization: Bearer <value of the
    // variable>.
    @Test
    void get_bearerAuth_sendsAuthorizationHeader() {
        final Result<HttpReply> result = exchange(Map.of(KEY_VARIABLE, KEY_VALUE))
                .get(config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of()), "/models");

        assertThat(result.isOk()).isTrue();
        server.verify(
                getRequestedFor(urlEqualTo(MODELS_PATH)).withHeader("Authorization", equalTo("Bearer " + KEY_VALUE)));
    }

    // IF the provider signs in with a named key header, THEN that header carries the value and no Authorization is
    // sent.
    @Test
    void get_apiKeyAuth_sendsNamedHeaderAndNoAuthorization() {
        exchange(Map.of(KEY_VARIABLE, KEY_VALUE))
                .get(config(ProviderAuth.API_KEY, "X-Api-Key", KEY_VARIABLE, Map.of()), "/models");

        server.verify(getRequestedFor(urlEqualTo(MODELS_PATH))
                .withHeader("X-Api-Key", equalTo(KEY_VALUE))
                .withoutHeader("Authorization"));
    }

    // IF a custom header holds ${env:NAME}, THEN the request carries the variable's value under that header.
    @Test
    void get_customHeaderEnvReference_isResolved() {
        exchange(Map.of("TEAM_NAME", "acme"))
                .get(config(ProviderAuth.NONE, null, null, Map.of("X-Team", "${env:TEAM_NAME}")), "/models");

        server.verify(getRequestedFor(urlEqualTo(MODELS_PATH)).withHeader("X-Team", equalTo("acme")));
    }

    // IF a literal custom header has no variable reference, THEN it is sent as written.
    @Test
    void get_literalCustomHeader_isSentAsWritten() {
        exchange(Map.of()).get(config(ProviderAuth.NONE, null, null, Map.of("X-Org", "acme")), "/models");

        server.verify(getRequestedFor(urlEqualTo(MODELS_PATH)).withHeader("X-Org", equalTo("acme")));
    }

    // IF the provider needs no sign-in, THEN no Authorization header is sent even when a reference is set.
    @Test
    void get_authNone_sendsNoAuthorization() {
        exchange(Map.of(KEY_VARIABLE, KEY_VALUE))
                .get(config(ProviderAuth.NONE, null, KEY_VARIABLE, Map.of()), "/models");

        server.verify(getRequestedFor(urlEqualTo(MODELS_PATH)).withoutHeader("Authorization"));
    }

    // IF the credential variable is not set, THEN the call fails with missingCredential naming the variable and sends
    // nothing.
    @Test
    void get_unsetCredentialVariable_failsWithMissingCredentialAndSendsNothing() {
        final Result<HttpReply> result =
                exchange(Map.of()).get(config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of()), "/models");

        assertThat(error(result).code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(error(result).message()).contains(KEY_VARIABLE);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF the credential variable is set to a blank value, THEN it counts as unset.
    @Test
    void get_blankCredentialValue_countsAsUnset() {
        final Result<HttpReply> result = exchange(Map.of(KEY_VARIABLE, "   "))
                .get(config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of()), "/models");

        assertThat(error(result).code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF a custom header's variable is not set, THEN the call fails with missingCredential naming it and sends nothing.
    @Test
    void get_unsetHeaderVariable_failsWithMissingCredentialAndSendsNothing() {
        final Result<HttpReply> result = exchange(Map.of())
                .get(config(ProviderAuth.NONE, null, null, Map.of("X-Team", "${env:TEAM_NAME}")), "/models");

        assertThat(error(result).code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(error(result).message()).contains("TEAM_NAME");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF a streamed call's credential variable is not set, THEN it fails before any request is made.
    @Test
    void postStreamed_unsetCredentialVariable_failsBeforeAnyRequest() {
        final Result<HttpReply> result = exchange(Map.of())
                .postStreamed(
                        config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of()),
                        "/models",
                        "{}",
                        Duration.ofSeconds(2),
                        Long.MAX_VALUE,
                        line -> true);

        assertThat(error(result).code()).isEqualTo(ErrorCode.missingCredential);
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF a streamed call has a credential, THEN the streamed request carries the bearer header too.
    @Test
    void postStreamed_bearerAuth_sendsAuthorizationHeader() {
        exchange(Map.of(KEY_VARIABLE, KEY_VALUE))
                .postStreamed(
                        config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of()),
                        "/models",
                        "{}",
                        Duration.ofSeconds(2),
                        Long.MAX_VALUE,
                        line -> true);

        server.verify(
                postRequestedFor(urlEqualTo(MODELS_PATH)).withHeader("Authorization", equalTo("Bearer " + KEY_VALUE)));
    }

    // IF a header variable is unset while a key is set, THEN no part of the error holds the key.
    @Test
    void get_missingHeaderVariable_errorNeverContainsTheKeyValue() {
        final Result<HttpReply> result = exchange(Map.of(KEY_VARIABLE, KEY_VALUE))
                .get(config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of("X-Team", "${env:TEAM_NAME}")), "/models");

        assertThat(describe(error(result))).doesNotContain(KEY_VALUE);
    }

    // IF the endpoint is unreachable with a key set, THEN no part of the transport error holds the key.
    @Test
    void get_unreachableEndpointWithKey_errorNeverContainsTheKeyValue() {
        final ProviderConfig closed = new ProviderConfig(
                "remote",
                ProviderKind.OPENAI_COMPATIBLE,
                ClosedPorts.endpoint("/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                null,
                ProviderAuth.BEARER,
                null,
                KEY_VARIABLE,
                null,
                null,
                Map.of(),
                List.of());

        final Result<HttpReply> result =
                exchange(Map.of(KEY_VARIABLE, KEY_VALUE)).get(closed, "/models");

        assertThat(error(result).code()).isEqualTo(ErrorCode.unreachable);
        assertThat(describe(error(result))).doesNotContain(KEY_VALUE);
    }

    // IF a resolved header value holds a line break, THEN the call is a validation error that does not echo the value.
    @ParameterizedTest
    @ValueSource(strings = {"acme\r\nX-Injected: 1", "acme\nX-Injected: 1", "acme\u0000"})
    void get_headerValueWithControlCharacter_isValidationWithoutEchoingValue(String value) {
        final Result<HttpReply> result = exchange(Map.of("TEAM_NAME", value))
                .get(config(ProviderAuth.NONE, null, null, Map.of("X-Team", "${env:TEAM_NAME}")), "/models");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(describe(error(result))).doesNotContain("acme").doesNotContain("X-Injected");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF the key itself holds a line break, THEN the call is a validation error and the key is not echoed.
    @Test
    void get_credentialValueWithLineBreak_isValidationWithoutEchoingValue() {
        final Result<HttpReply> result = exchange(Map.of(KEY_VARIABLE, "sk-secret-777\nX-Injected: 1"))
                .get(config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of()), "/models");

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(describe(error(result))).doesNotContain("sk-secret-777");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // IF a custom header is also named Authorization, THEN the configured sign-in wins and one value is sent.
    @Test
    void get_customAuthorizationHeaderAndBearerAuth_authWins() {
        exchange(Map.of(KEY_VARIABLE, KEY_VALUE, "OTHER_AUTH", "Bearer other"))
                .get(
                        config(ProviderAuth.BEARER, null, KEY_VARIABLE, Map.of("Authorization", "${env:OTHER_AUTH}")),
                        "/models");

        assertThat(server.getAllServeEvents())
                .singleElement()
                .satisfies(event -> assertThat(event.getRequest()
                                .getHeaders()
                                .getHeader("Authorization")
                                .values())
                        .containsExactly("Bearer " + KEY_VALUE));
    }

    private static HttpExchange exchange(Map<String, String> env) {
        return new HttpExchange(new HttpClients(), System::nanoTime, env::get);
    }

    private ProviderConfig config(
            ProviderAuth auth,
            @Nullable String apiKeyHeader,
            @Nullable String credentialRef,
            Map<String, String> headers) {
        return new ProviderConfig(
                "remote",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                null,
                auth,
                apiKeyHeader,
                credentialRef,
                null,
                null,
                headers,
                List.of());
    }

    private static AppError error(Result<HttpReply> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private static String describe(AppError error) {
        return error.title() + "|" + error.message() + "|" + error.details() + "|" + error.cause();
    }
}
